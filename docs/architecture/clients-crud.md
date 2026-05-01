# MVP-4: Clients CRUD

## Goal

Manage clients of an inbound on a 3x-ui panel from the Android app: list,
add, edit, delete, reset traffic. Mirror the upstream telegram-bot UX.
Supported protocols for MVP-4: **VMESS, VLESS, Shadowsocks**. **Trojan
deferred to backlog** (user does not use it; the bootstrapping risk on
update — `oldPassword` as URL key — needs a live spike that we are not
running for MVP). Other protocols render read-only — only `clientStats`
traffic counters; mutating actions hidden.

Source of truth: clients live inside `InboundDto.settings` as a JSON-encoded
`clients[]` string. There is **no list-clients endpoint** — we parse
`settings` locally. `clientStats` is traffic data only, not identity/config.

## 1. Module placement

Use the `:feature:clients` module already declared in `architecture.md` §2 —
empty today. `:feature:inbounds` is opinionated (toggle, delete) and would
grow large with a wizard + edit form; share-link work in `:feature:share`
will reuse the same data model. Trade-off: one extra Gradle module + Hilt
graph — worth it for blast-radius isolation.

## 2. Navigation

No `InboundDetailScreen`. The bottom-bar `Clients` tab opens
`ClientsListScreen` with a top-bar dropdown for inbound selection (mirrors
`:feature:inbounds`). The inbounds list overflow gets a "Manage clients"
action that navigates with a preselected inbound.

| Route | Args |
|---|---|
| `clients/{inboundId}` | `inboundId: Int` (path) |
| `clients/{inboundId}/add` | `inboundId: Int` |
| `clients/{inboundId}/edit/{clientKey}` | `inboundId: Int`, `clientKey: String` (URL-encoded) |

`clientKey` is the protocol-dependent identifier (see §5). Delete and reset
are dialogs over the list — no dedicated routes.

## 3. Data shapes

New file `:core:xui.dto.ClientConfig` — sealed hierarchy with
kotlinx-serialization:

```kotlin
sealed interface ClientConfig {
    val email: String
    val enable: Boolean
    val totalGB: Long      // bytes; 0 = unlimited
    val expiryTime: Long   // epoch millis; 0 = no expiry
    val limitIp: Int       // 0 = unlimited
    val subId: String
    val comment: String
    val tgId: String       // upstream-required, default ""
    val reset: Int         // upstream-required, default 0

    data class Vmess(val id: String, ...) : ClientConfig
    data class Vless(val id: String, val flow: String, ...) : ClientConfig  // "" | "xtls-rprx-vision"
    data class Trojan(val password: String, ...) : ClientConfig
    data class Shadowsocks(val password: String, val method: String, ...) : ClientConfig
}
```

Hand-written `ClientConfigSerializer` parameterised by the inbound's
`protocol` string — upstream JSON has no polymorphic discriminator.

```kotlin
object ClientsJson {
    fun parse(protocol: String, settingsJson: String): List<ClientConfig>
    fun encodeAddPayload(protocol: String, client: ClientConfig): String
    // → {"clients":[{...one client...}]}
}
```

`InboundDto` stays untouched. Parsing happens in `:core:xui`; UI receives
domain `ClientConfig` values.

## 4. Repository surface

Extend `XuiClient` directly — keep the existing facade pattern:

```kotlin
suspend fun addClient(...,    inboundId: Int, ClientConfig): Result<Unit, DomainError>
suspend fun updateClient(..., inboundId: Int, clientKey: String, newConfig: ClientConfig)
suspend fun deleteClient(..., inboundId: Int, clientKey: String)
suspend fun resetClientTraffic(..., inboundId: Int, email: String)
```

Listing reuses `fetchInbounds`: VM picks the inbound by id and calls
`ClientsJson.parse(inbound.protocol, inbound.settings)`. We do **not** add
`fetchInbound(id)` — list is small, comes with traffic stats, avoids a
second round-trip. Add it later only if payload size becomes a problem.

## 5. API mapping

Verified against upstream `web/service/inbound.go` (`DelInboundClient`,
`UpdateInboundClient`, `ResetClientTraffic`):

**All bodies are `application/x-www-form-urlencoded`** (verified via live
capture 2026-04-30, see `core/xui/src/test/resources/clients/REQUESTS.md`).
Add and update use two form fields: `id` (inboundId, integer) and `settings`
(URL-encoded JSON string).

| Action | Method + path | `clientKey` rule | Form fields |
|---|---|---|---|
| Add client | `POST /panel/api/inbounds/addClient` | — | `id=<inboundId>`, `settings=<json {"clients":[…]}>` |
| Update client | `POST /panel/api/inbounds/updateClient/{clientKey}` | VMESS/VLESS: `client.id` (UUID); Shadowsocks: `client.email` | same shape as add; settings JSON additionally carries `created_at` (preserve from existing) and `updated_at` (epoch ms) |
| Delete client | `POST /panel/api/inbounds/{inboundId}/delClient/{clientKey}` | same as update | empty body |
| Reset traffic | `POST /panel/api/inbounds/{inboundId}/resetClientTraffic/{email}` | always `email` | empty body |

The protocol→key rule lives in one function, `ClientConfig.urlKey(protocol)`
— prevents the matrix from leaking into UI.

## 6. UI

- **`ClientsListScreen`** — inbound dropdown, client list, per-row menu
  (Edit / Reset traffic / Delete). Read-only banner if protocol unsupported.
- **`ClientFormScreen`** — single Compose form, **not** a multi-step wizard.
  Step wizards are bot-style; on a phone a scrollable form is faster.
  Sections: identity (UUID/password + generate button), email, limits
  (totalGB, expiryTime via M3 DatePicker, limitIp), flags (enable),
  metadata (subId, comment). Same composable serves add and edit; edit
  pre-fills and disables identity for vmess/vless. For trojan/ss the
  password field is editable but warns "this re-keys the client".
- Delete/reset confirms: reuse the `InboundDeleteConfirmDialog` pattern.

## 7. Generators

Add `:core:xui.util.ClientSecrets` (not `:core:crypto` — non-cryptographic
identity material, no Keystore):

- `randomUuid()` — `UUID.randomUUID()`
- `randomTrojanPassword()` — 10 chars `[a-z0-9]`, `SecureRandom` (upstream
  `randomLowerAndNum(10)`)
- `randomShadowsocksPassword()` — 32 random bytes, Base64 (upstream
  `randomShadowSocksPassword`)
- `randomSubId()` — 16 chars `[a-z0-9]`

## 8. Errors

Same pattern as inbound ops. Existing `Throwable.toDomainError` already maps
`SpkiPinMismatchException` → `DomainError.PinMismatch` → kill-switch.
Business `{success:false, msg}` → `DomainError.PanelResponse(0, msg)`.
Form validation (UUID format, non-negative numbers) gates the submit button
— does not produce `DomainError`.

## 9. Migration

None. Clients are not persisted locally; round-trip the panel. Room schema
unchanged. No version bump.

## 10. Tests

- `ClientsJsonTest` — round-trip per protocol against fixtures captured
  from a real 3x-ui response (under `core/xui/src/test/resources/clients/`).
  Guards against breaking the exact JSON shape upstream parses.
- `ClientConfigUrlKeyTest` — protocol→key matrix.
- `ClientSecretsTest` — alphabet/length.
- `ClientsViewModelTest` — fake `XuiClient`, state transitions for
  add/edit/delete/reset and error → snackbar mapping.
- `ClientFormValidationTest` — empty UUID rejected, past expiry warned not
  blocked (upstream allows).

## 11. Open questions — status after live capture (2026-04-30)

1. ✅ **`addClient` body shape** — verified: form-urlencoded `id` +
   `settings`; `settings` JSON has the `{"clients":[…]}` wrapper.
2. ⏭ **Trojan password edit semantics** — moot, Trojan deferred to backlog.
3. ⏭ **Pin upstream version** — N/A while we have a live capture; revisit
   when Trojan/extra protocols re-enter scope.
4. ✅ **`reset` and `tgId` defaults** — verified: `reset=0`, `tgId=""` are
   sent on add; always include them.
5. ⏸ **Negative `expiryTime` (lazy expiry)** — MVP-4 writes non-negative
   only; documented and skipped.
6. ⏸ **`resetClientTraffic` body** — not yet captured live; assumed empty
   per upstream code. Confirm on first live test.
