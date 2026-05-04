# MVP-5: Share config

## Goal

Generate a per-client connection URI and QR code for an existing client of an
inbound on a 3x-ui panel, fully on the device — no `/sub` endpoint involved.
Output is the standard v2rayN-compatible link plus a copy-button and a QR
bitmap. Supported protocols: **VMESS, VLESS, Shadowsocks** (matching MVP-4).

**Not in scope.** Subscription link / `/sub/{subId}` endpoint is **explicitly
deferred to backlog** — sub-port is not part of `Panel` and there is no
known way to retrieve it from the panel API without a live spike. Trojan is
also out (deferred earlier in MVP-4). External proxies (`externalProxy[]` in
streamSettings) are **ignored** — we always emit one link against
`Panel.baseUrl` host. No client-side URI parsing (read-only output).

## 1. Module placement

- `:core:xui` — pure logic.
  - `dto/StreamSettings.kt` — sealed model for the parsed `streamSettings` blob.
  - `dto/StreamSettingsJson.kt` — parser, mirrors `ClientsJson` style.
  - `share/ClientUri.kt` — URI builder (`object`, no DI).
  - `share/ShareError.kt` — local error sealed class.
- `:feature:share` — UI only.
  - Adds dependencies: `:core:xui`, `:core:data` (read active panel),
    `libs.zxing.core` (already available transitively via
    `com.journeyapps:zxing-android-embedded` in catalog per architecture.md
    §11; we want `core` only — confirm in catalog, otherwise add
    `com.google.zxing:core` directly).
  - `ClientShareScreen.kt`, `ClientShareViewModel.kt`, navigation file.

`:feature:clients` already depends on `:core:xui` — adding a `Share` overflow
item that calls `navigateToShare(...)` requires no new dependency.

## 2. Navigation

| Route | Args |
|---|---|
| `share/{inboundId}/{clientKey}` | `inboundId: Int` (path), `clientKey: String` URL-encoded (path) |

Entry point: overflow menu item `Share` in `ClientCard` (between `Edit` and
`Reset traffic`) shown only when `isSupported` (same gate as edit/delete).
Visible regardless of `client.enable` — sharing a disabled client's link is
useful while keeping the slot inactive.

`ClientShareViewModel` rebuilds the full client object from the parent's
inbound list — same trick as `ClientFormScreen` (read shared
`ClientsViewModel` via `getBackStackEntry(ClientsListRoute)`). Trade-off:
share screen depends on the clients list being in memory. Acceptable since
the only entry point is from that list. If the back stack is gone (process
death deep-link case) — ViewModel falls back to a one-shot
`XuiClient.fetchInbounds(activePanel)` to reload.

## 3. streamSettings parsing

Critical and brittle: `InboundDto.streamSettings` is a JSON-as-string blob
whose schema mirrors Xray's `StreamConfig`. **No live fixture is committed
yet** — we have only `vless_add_settings.json` (the inbound `settings`,
which holds clients, not streamSettings). First task of implementation is
to capture a live VLESS+Reality streamSettings blob from the user's panel
and add it under `core/xui/src/test/resources/share/`.

### Sealed model

```kotlin
sealed interface StreamSettings {
    val network: String     // "tcp" | "ws" | "grpc" | "httpupgrade" | "xhttp" | "kcp" | "http"
    val security: Security  // None | Tls(...) | Reality(...)

    data class Tcp(val headerType: String?, val httpPath: String?, val httpHost: String?, ...) : StreamSettings
    data class Ws(val path: String?, val host: String?) : StreamSettings
    data class Grpc(val serviceName: String, val authority: String?, val multiMode: Boolean) : StreamSettings
    data class HttpUpgrade(val path: String?, val host: String?) : StreamSettings
    data class XHttp(val path: String?, val host: String?, val mode: String?) : StreamSettings
    // kcp/http considered Unsupported for MVP-5 — fall back to error
}

sealed interface Security {
    data object None : Security
    data class Tls(val sni: String?, val alpn: List<String>, val fingerprint: String?) : Security
    data class Reality(val sni: String?, val pbk: String?, val sid: String?, val fp: String?) : Security
}
```

### Parser contract

```kotlin
object StreamSettingsJson {
    fun parse(raw: String): Result<StreamSettings, ShareError>
}
```

- Hand-written, kotlinx-serialization JSON tree API (same approach as
  `ClientsJson`) — no auto-generated DTO; upstream schema mutates between
  3x-ui versions and we only need a small projection.
- `network` taken from root `network` field.
- For each known network: read the matching `*Settings` sub-object; ignore
  unknown fields silently.
- For `security`: dispatch on root `security`. Reality fields read from
  `realitySettings.{serverNames[0], publicKey, shortIds[0], fingerprint}` —
  the pick-random behaviour upstream uses is replaced by **always pick
  index 0**: deterministic output for the same client, easier round-trip
  testing. Document the divergence in the Open questions.
- Returns `ShareError.UnsupportedTransport(network)` for kcp/http or any
  unknown network. **Does not** throw — UI shows raw error.

## 4. URI format (verified against upstream `sub/subService.go`)

### VLESS

```
vless://{uuid}@{host}:{port}?{params}#{remarkUrlEncoded}
```

Params, in stable order (alphabetical, like upstream `buildLinkWithParams`):

| Network | Always | Conditional |
|---|---|---|
| any | `type=<network>` | `encryption=<inboundEncryption>` if present in inbound `settings` |
| `tcp` (http header) | — | `path`, `host`, `headerType=http` |
| `ws` | — | `path`, `host` |
| `grpc` | `serviceName` | `authority`, `mode=multi` if multiMode |
| `httpupgrade` / `xhttp` | — | `path`, `host` (xhttp adds `mode`) |

Security suffix:

| security | Params |
|---|---|
| `tls` | `security=tls`, `sni?`, `alpn?` (CSV), `fp?`; if `network=tcp` and client has `flow` — `flow=<flow>` |
| `reality` | `security=reality`, `sni?`, `pbk?`, `sid?`, `fp?`; same `flow` rule |
| anything else | `security=none` |

`pqv` and `spx` Reality params (post-quantum + spiderX path) are **omitted**
in MVP-5 — `spx` is randomized server-side (`/` + 15-char nonce) and not
needed for client connection; `pqv` requires mldsa65 support unclear in our
target client apps. Document in Open questions.

`remark` = `inbound.remark + "-" + client.email` (mirrors upstream
`genRemark` simplified — no separator config). URL-encoded as a fragment.

### VMESS

```
vmess://{base64Std(JSON)}
```

JSON keys (v2rayN schema, all strings except `port`/`aid`):

```json
{
  "v": "2",
  "ps": "<remark>",
  "add": "<host>",
  "port": <inbound.port>,
  "id": "<client.uuid>",
  "aid": "0",
  "scy": "<client.security || \"auto\">",
  "net": "<network>",
  "type": "none" | "<tcp.header.type>",
  "host": "<derived from net>",
  "path": "<derived from net>",
  "tls": "<security>",
  "sni": "<...>",
  "alpn": "<csv>",
  "fp": "<...>"
}
```

Note: `ClientConfig.Vmess` does **not** carry `security` (`scy`) field today
— add a new optional field `security: String? = null` (defaults to
`"auto"`) **only if the field exists in real fixtures**. Open question
flagged. Without it we emit `"scy":"auto"` always.

Base64: standard alphabet, **with** padding (`Base64.encodeToString(... ,
NO_WRAP)`). `vmess://` consumers tolerate both with/without padding; match
upstream.

### Shadowsocks (SIP002)

```
ss://{base64Std(method:password)}@{host}:{port}?{params}#{remark}
```

- Userinfo: `Base64.encodeToString("$method:$password".toByteArray(),
  Base64.NO_WRAP)`. Upstream uses **standard** alphabet, not URL-safe; we
  match it byte-for-byte.
- For 2022 ciphers (method starts with `2`, e.g. `2022-blake3-aes-256-gcm`)
  the userinfo becomes `method:inboundPassword:clientPassword`. **Inbound
  password is read from `inbound.settings.password`** — extend
  `ClientsJson` with a thin `parseInboundPassword(settingsJson): String?`
  helper rather than expanding `ClientConfig`. If absent → emit
  `ShareError.MissingInboundPassword`.
- Query params: same `applyShareNetworkParams` + `applyShareTLSParams`
  rules as VLESS (most SS deployments are `tcp` + `none`, so usually no
  params).

### Builder API

```kotlin
object ClientUri {
    fun build(
        inbound: InboundDto,
        client: ClientConfig,
        host: String,
    ): Result<String, ShareError>
}
```

- `host` is computed by VM from `Panel.baseUrl`: parse as `Uri`, take
  `host`. If `Panel.baseUrl` host is a private IP / `localhost` — still
  emit; user knows their topology. No reverse-DNS, no override.
- The protocol switch is internal (`when (client) is Vmess/Vless/Ss`).
- Pure function: no IO, no Android dependencies — testable in JVM tests.

## 5. UI

`ClientShareScreen` — single scrollable column:

1. Top app bar: `Share — <client.email>`, back button.
2. Protocol/inbound chip line: `VLESS · 443 · panel.example.com`.
3. **QR card** — square, side = min(screen width, 320dp). Centered.
4. **URI block** — `OutlinedTextField` (read-only, `singleLine = false`,
   monospace) + `IconButton(Icons.Default.ContentCopy)` on the right that
   copies to clipboard via `ClipboardManager` and shows a Snackbar
   `Copied`.
5. On `ShareError` — replace QR + URI with an `ErrorState` composable:
   icon, message, **fallback "Copy raw" button** that copies the inbound's
   raw `streamSettings` JSON for power users. Specifically for
   `UnsupportedTransport` only — for `MissingInboundPassword` /
   `InvalidStreamSettings` show a plain error.

`FLAG_SECURE` is already set on the activity (architecture.md §9). No
extra screenshot guard needed.

`ClientShareViewModel`:

```kotlin
data class State(
    val email: String,
    val protocol: String,
    val result: Result<ShareData, ShareError>,
)
data class ShareData(val uri: String, val qrBitmap: Bitmap)
```

ViewModel resolves inbound + client + active panel host, calls
`ClientUri.build`, then `QrEncoder.encode(uri, sizePx, dark, light)` to
build the bitmap. Emits a single `State` — no Loading flicker (everything
is local + sub-second).

## 6. QR generation

- Library: `com.google.zxing:core` (pure JVM, no Android resources). Avoid
  `zxing-android-embedded` — it pulls camera/UI we do not need. Add
  explicit catalog entry `zxing-core = "com.google.zxing:core:3.5.3"`.
- Encoder: `MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, sizePx,
  sizePx, mapOf(EncodeHintType.MARGIN to 1, ERROR_CORRECTION to L))`.
  Error correction L — links are short, want denser QR for reliable scan.
- Output: `Bitmap.Config.ARGB_8888`, foreground = theme onSurface,
  background = theme surface — recompute on dark/light theme change.
- Render via `Image(painter = remember(bitmap){ BitmapPainter(...) })`.
- Generation runs on `Dispatchers.Default` inside VM init (≤50ms typical).

Trade-off: re-encoding on theme switch (cheap) over caching across themes
(memory + state plumbing).

## 7. Errors

Local sealed class — does not extend `DomainError`, no network involved:

```kotlin
sealed class ShareError {
    data class UnsupportedTransport(val network: String) : ShareError()
    data class InvalidStreamSettings(val reason: String) : ShareError()
    data object MissingInboundPassword : ShareError()        // SS-2022 only
    data class UnsupportedProtocol(val protocol: String) : ShareError()
}
```

VM keeps `ShareError` directly in state; UI maps to `stringResource`.
Crash pipeline (`SpkiPinMismatchException` etc.) is irrelevant here — no
HTTP. The only IO path (fallback re-fetch on dead back stack) goes through
`XuiClient.fetchInbounds` and yields normal `DomainError`, surfaced as a
generic Snackbar.

## 8. Tests

- **`StreamSettingsJsonTest`** — fixtures under
  `core/xui/src/test/resources/share/`:
  - `vless_reality_tcp.json`, `vless_tls_ws.json`, `vmess_tls_grpc.json`,
    `ss_tcp_none.json` (capture from live panel; minimum: one VLESS
    Reality which is the user's actual config).
  - For each: assert sealed variant, key fields, security branch.
  - Negative: `kcp_unsupported.json` → `UnsupportedTransport("kcp")`;
    malformed JSON → `InvalidStreamSettings`.

- **`ClientUriTest`** — round-trip:
  - VLESS + Reality + flow=`xtls-rprx-vision` → exact-string compare with a
    captured-from-real-panel `expected_uri.txt` (acquired via panel UI
    "Copy URL" button on the same client). This is the only reliable way
    to lock the format byte-for-byte.
  - VMESS: build URI, base64-decode payload, JSON-compare with expected
    map (string compare on base64 is fragile — JSON map order differs).
  - SS: exact-string compare; for SS-2022 verify three-segment userinfo.

- **`ClientShareViewModelTest`** — fake `XuiClient` returning the parent
  inbound list; assert `State.result` is `Success` for happy path,
  `UnsupportedTransport` for kcp inbound, `MissingInboundPassword` when
  inbound `settings` lacks `password` for SS-2022 method.

- **No instrumented tests for QR.** ZXing is well-tested upstream.

## 9. Open questions

1. ✅ **Live streamSettings fixture** — not captured (live data carries
   Reality private keys; user opted out). Parser + builder derived from
   upstream Go source (`sub/subService.go`, `xray/inbound.go`). Synthetic
   fixtures in `core/xui/src/test/resources/share/` with private fields
   set to `"REDACTED"`. Risk: edge fields may surface only on first real
   panel test — patch then.
2. ⏸ **`vmess` `scy` field origin.** Upstream reads `clients[i].Security`.
   Our `ClientConfig.Vmess` does not carry it; we hardcode `"scy":"auto"`.
   Confirm against a live VMESS inbound when one becomes available; if
   real clients have non-auto values, extend `ClientConfig.Vmess` with
   optional `security: String? = null`.
3. **Reality `pqv` and `spx`** — needed by modern Xray clients? Test
   v2rayNG / Streisand / Hiddify against generated link without them. If
   they fail: add `spx` as random `/15chars` per call.
4. **Reality `serverNames` / `shortIds` index pick.** Upstream picks
   random; we pick index 0. Confirm clients accept any element of the
   set (they should — the server side rotates).
5. **Inbound-level Shadowsocks password for non-2022 methods.** Some
   deployments still set `inbound.settings.password` even when method is
   not 2022. Decision: ignore it for non-2022, follow upstream which uses
   only the client password.
6. **`flow` placement for VLESS-WS.** Upstream gates `flow` behind
   `streamNetwork == "tcp"`. We mirror exactly — flow is dropped for
   ws/grpc even if present in client. Document this so users do not
   wonder why their non-TCP flow is missing.
7. **External proxy entries.** `streamSettings.externalProxy[]` lets the
   panel emit links pointing at CDN/proxy hosts instead of the panel
   host. We ignore this for MVP-5 (one link, panel host). Re-evaluate
   when user requests it.
