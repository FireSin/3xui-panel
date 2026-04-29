# TLS pinning for self-signed panels (TOFU + SPKI)

## Goal

Replace the blanket `NoOpTrustManager` + `hostnameVerifier{_,_->true}` with **TOFU +
SPKI-leaf pinning** for panels with certs the system store does not recognize. CA-signed
panels stay on system trust unchanged. Pin scheme is **leaf-cert SPKI SHA-256** — minimal,
survives leaf rotation that keeps the same key; chain pinning, share-link certs, and
user pin import/export are out of scope.

## 1. Data model

Replace boolean `trustSelfSigned` with explicit per-panel TLS mode. New `PanelEntity`
columns:

- `tls_mode TEXT NOT NULL DEFAULT 'SYSTEM'` — `SYSTEM` | `PINNED`
- `pinned_spki_sha256 TEXT` — base64; null when `tls_mode = SYSTEM`
- `pinned_at INTEGER` — epoch millis; shown in edit screen

Domain `Panel` and `PanelDraft` mirror the columns; `PanelTlsMode` enum lives in
`:core:data` model package. UI does not type the SPKI — repository fills it after probe.
Keep `trust_self_signed` column physically (drop in a follow-up version) so the schema
diff stays small.

### Migration v1 → v2

```sql
ALTER TABLE panels ADD COLUMN tls_mode TEXT NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE panels ADD COLUMN pinned_spki_sha256 TEXT;
ALTER TABLE panels ADD COLUMN pinned_at INTEGER;
UPDATE panels SET tls_mode = 'PINNED' WHERE trust_self_signed = 1;
```

`AppDatabase` bumps to `version = 2`; register the migration in the Hilt module.

## 2. Pin capture

Capture during `XuiClient.probeLogin` when the user picked self-signed. We need the leaf
exactly as presented on this connection, regardless of TrustManager outcome. Use OkHttp
`EventListener.connectionAcquired` — read `connection.handshake().peerCertificates.first()`.
Fires once per connection, symmetric for both modes.

The probe builds a transient client with a `CapturingTrustManager` wrapping the system
TM: tries system validation first, accepts on failure (probe is the explicit trust
gesture), and publishes the leaf into a `CompletableDeferred<X509Certificate>` either
way. Hostname verifier stays default — self-signed certs typically have correct SAN; if
not, probe fails loud (good).

`probeLogin` returns `Result<ProbeOutcome, DomainError>` with
`ProbeOutcome(capturedSpki: String?)`. `PanelRepositoryImpl.add()` writes that into the
entity together with `pinned_at = now`.

## 3. Runtime enforcement

```kotlin
internal class PinningTrustManager(
    private val system: X509TrustManager,
    private val pinnedSpki: String,
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
        val spki = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded),
            Base64.NO_WRAP,
        )
        if (spki != pinnedSpki) throw SpkiPinMismatchException(spki)
        // do NOT call system.checkServerTrusted — self-signed will fail it
    }
    override fun checkClientTrusted(c: Array<out X509Certificate>, a: String) =
        system.checkClientTrusted(c, a)
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
```

Hostname verifier: keep OkHttp default. SPKI pinning does not subsume SAN — we keep both.

`OkHttpClientFactory.getClient(panelId, tlsMode, pinnedSpki)` is the new shape. Pass the
pin from the call site (already happens for `trustSelfSigned`) — no `:core:network` →
`:core:data` coupling. Wrap `(tlsMode, pinnedSpki)` as `PanelTls` value class in
`:core:common`. `ClientKey` includes the pin so re-pin invalidates cached clients
naturally; `invalidate(panelId)` still wipes by id.

## 4. Pin mismatch UX

Add `DomainError.PinMismatch(panelId: String, observedSpki: String)`. `XuiClient`
maps `SpkiPinMismatchException` at the existing `Throwable.toDomainError()` site.

Mismatch can hit on any call. Add `PinMismatchEvents` (a `SharedFlow` in `:core:common`)
which `XuiClient` emits on. `MainActivity` collects it and shows a global blocking
dialog: *"Certificate changed for `<panel>`. Could be rotation, could be MITM. Open
panel settings to re-verify and re-pin."* **Never auto-re-pin.** Dismiss = stay logged
out.

`PanelAddEditScreen` in edit mode shows `pinned_at` and a "Re-probe and re-pin" action
that re-runs `probeLogin` and overwrites the pin on success.

## 5. Migration of existing self-signed panels

**Lazy TOFU on first use after update.** Migration sets `tls_mode = PINNED` with
`pinned_spki_sha256 = NULL`. On the first request `OkHttpClientFactory` sees `PINNED` +
null pin → builds a one-shot `CapturingTrustManager`, captures SPKI, persists via a
`PanelPinWriter` callback, then proceeds. Equivalent to current "trust everything" for
exactly one call; subsequent calls use strict pinning. No regression, no scary first-run
dialog. Sentinel-based forced re-probe rejected for the same reason.

## 6. Module impact

- `:core:common` — `DomainError.PinMismatch`, `PinMismatchEvents`, `PanelTls`,
  `PanelPinWriter` SPI (interface only — keeps `:core:network` clean of `:core:data`).
- `:core:network` — `PinningTrustManager`, `CapturingTrustManager`,
  `SpkiPinMismatchException`; `OkHttpClientFactory` API swap; `EventListener` for
  capture. Optional `PanelPinWriter` injection for lazy capture.
- `:core:data` — entity/model/mapper/migration; `PanelPinWriterImpl` bound in Hilt.
- `:core:xui` — `ProbeCredentials`, `XuiClient` swap bool → `PanelTls`; emit on
  `PinMismatchEvents`.
- `:feature:panels` — form: dropdown ("System CA" / "Self-signed with pinning") +
  warning copy update; edit screen shows pin status + re-probe button.
- `:app` — `MainActivity` hosts the mismatch dialog.

No cyclic-dependency risk: writer SPI in `:core:common`, no new edges.

## 7. Test surface

- `PinningTrustManagerTest` — match passes; mismatch throws `SpkiPinMismatchException`;
  empty chain throws; `checkClientTrusted` delegates to system.
- `SpkiHasherTest` — known cert → known base64 (golden).
- `CapturingTrustManagerTest` — leaf captured even when system trust rejects.
- `OkHttpClientFactoryTest` — cache key includes pin; `invalidate(panelId)` clears all
  modes for that id.
- `PanelRepositoryImplTest` — `add()` persists captured SPKI + `pinned_at`; edit
  re-probe overwrites both.
- `XuiClientTest` — `SpkiPinMismatchException` → `DomainError.PinMismatch`; emits on
  `PinMismatchEvents`.
- `MigrationTest` (Room v1 → v2 harness) — `trust_self_signed=1` row → `tls_mode='PINNED'`,
  null pin.
- Lazy-capture integration test — first call with null pin populates it via fake
  `PanelPinWriter`, second call enforces strictly.

## 8. Out of scope (recap)

- Full chain pinning — leaf SPKI is enough against MITM and tolerant to typical
  same-key rotation on self-signed servers.
- Subscription / share-link cert handling.
- User-managed pin import/export.
