# MVP-6: Current traffic statistics (A)

## Goal

Single read-only screen that shows the **current** traffic counters for the
active panel: server-wide totals, per-inbound counters, and per-client
counters (with an "online now" badge). Pull-to-refresh, no history, no
charts. The screen is a power-user dashboard that compresses what the web UI
shows under the *Inbounds* table into one place.

## Explicit non-scope

- **No history / time series.** Upstream 3x-ui keeps no per-inbound or
  per-client traffic snapshots; it only exposes the live counters
  (resettable). Any storage / `WorkManager` sampler / Vico charts is
  deferred to **MVP-6b** (graphs) and out of scope here. This was confirmed
  by the spike documented in `docs/architecture/share-config.md` and commit
  `b0ed292`.
- **No mutations.** Reset / disable / delete / edit live in `:feature:clients`
  and are intentionally not duplicated here.
- **No real `lastOnline` timestamp.** Upstream's `lastOnline` handler returns
  only the most recent ~10 sessions and is unreliable as a presence
  indicator. We use the `onlines` handler (a flat list of currently-online
  emails) and surface a binary "online now" badge — no "last seen X
  minutes ago".
- **No `XuiClient.fetchInbounds` signature change.** New `fetchOnlines`
  facade is added alongside it.

## 1. Data flow

```
StatsViewModel.load()
   └─ PanelRepository.observeActive().first()              // active Panel
   └─ coroutineScope {
         val inbounds = async { XuiClient.fetchInbounds(...) }
         val onlines  = async { XuiClient.fetchOnlines(...) }
         awaitAll(...)
      }
   └─ inbounds = REQUIRED:    Failure → StatsUiState.Error
   └─ onlines  = OPTIONAL:    Failure → log + emptySet, continue
   └─ build StatsUiState.Content(panel, summary, inbounds, expandedIds=∅, onlineEmails)
```

Parallel `async` (not sequential) — calls are independent and we want one
visible refresh tick. `fetchInbounds` is the only call whose failure blocks
the screen; `fetchOnlines` is best-effort. We **do not** retry `onlines`
on auth failure separately — `withSession` already handles 401 reauth, and
if the inbounds call refreshed the cookie the parallel onlines call may
race; if both fail with 401 the onlines branch silently degrades and the
inbounds branch shows the error. Acceptable: the only consequence of a
race is a single missing badge until pull-to-refresh.

`StatsViewModel.refresh()` keeps current `Content` visible and toggles a
separate `isRefreshing: StateFlow<Boolean>` (same pattern as
`DashboardViewModel`) so `PullToRefreshBox` works without flashing
`Loading`.

## 2. New API: `onlines`

Upstream handler: `web/controller/api/inbound.go::onlines` →
`POST /panel/api/inbounds/onlines`, body empty, returns
`{"success":true,"obj":["email1","email2",...]}`.

> Note: the existing `XuiApi.onlineClients()` declaration on
> `/panel/inbound/onlines` (no `/api/`) returning `LoginResponseDto` is a
> stale stub and **must be removed** as part of this MVP — it points at
> the wrong path and the wrong DTO.

### DTO (`:core:xui`)

```kotlin
// dto/OnlinesDto.kt
@Serializable
data class OnlinesResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<String>? = null,
    @SerialName("msg") val msg: String? = null,
)
```

### Retrofit (`XuiApi`)

```kotlin
@POST("/panel/api/inbounds/onlines")
suspend fun onlines(): Response<OnlinesResponseDto>
```

### Facade (`XuiClient`)

```kotlin
suspend fun fetchOnlines(
    panelId: String, baseUrl: String,
    username: String, password: String, tls: PanelTls,
): Result<Set<String>, DomainError>
```

Mirrors `fetchInbounds` exactly: `withSession` + `runCatching` + same
`toDomainError` pipeline. Returns a `Set<String>` (membership lookup is
the only operation the UI does).

## 3. UI state model

`:feature:stats/StatsUiState.kt`

```kotlin
sealed class StatsUiState {
    data object NoActivePanel : StatsUiState()
    data object Loading : StatsUiState()
    data class Error(val error: DomainError) : StatsUiState()
    data class Content(
        val panel: Panel,
        val summary: ServerSummary,
        val inbounds: List<InboundDto>,           // upstream order, as-returned
        val expandedIds: Set<Int>,                // UI-only, persists across refresh
        val onlineEmails: Set<String>,            // empty if onlines failed
        val onlinesAvailable: Boolean,            // false → hide badges entirely
    ) : StatsUiState()
}

data class ServerSummary(
    val totalUp: Long,
    val totalDown: Long,
    val inboundCount: Int,
    val activeClientCount: Int,   // sum of clientStats where enable=true
)
```

VM exposes:
- `uiState: StateFlow<StatsUiState>`
- `isRefreshing: StateFlow<Boolean>`
- `fun refresh()`, `fun toggleExpanded(inboundId: Int)`, `fun retry()`

`expandedIds` lives in VM (not Compose) so it survives configuration
changes and pull-to-refresh.

## 4. Pure utilities (`:core:common`)

Placed in `:core:common` because they are framework-free and reusable;
two units are wanted, both table-driven testable.

```kotlin
// core/common/.../format/PrettyBytes.kt
/** "0 B" / "942 B" / "1.5 KB" / "12.7 GB" — single-decimal, US locale. */
fun prettyBytes(bytes: Long): String

// core/common/.../format/PrettyExpiry.kt
/** 0 → "∞"; future → "через 12 дней"; past → "истёк 3 дня назад". */
fun prettyExpiry(epochMs: Long, nowMs: Long, locale: Locale = Locale.getDefault()): String
```

### Why a new `prettyBytes` instead of reusing `core.designsystem.format.formatBytes`?

`formatBytes` emits two-decimal precision (`"1.23 GB"`) — too noisy for
dense rows of per-client counters. New helper is one decimal and uses
`B/KB/MB/GB/TB` (no PB — irrelevant for client traffic). Both functions
coexist; dashboard keeps the existing one. Trade-off: small duplication,
but visually meaningful difference and avoids a UX-driven change to
already-shipped dashboard code.

### `prettyExpiry`

- `epochMs == 0L` → `"∞"` (matches upstream "no expiry" sentinel).
- `delta = epochMs - nowMs`.
- Buckets: `<60s`, `<1h`, `<1d`, `<30d`, otherwise days. Format strings
  fed in from `:feature:stats` `strings.xml` so plurals work — but the
  **function itself** stays UI-free by returning a small sealed result
  consumed by a Composable. Concretely:

```kotlin
sealed interface ExpiryLabel {
    data object Never : ExpiryLabel
    data class InFuture(val unit: TimeUnit, val amount: Long) : ExpiryLabel
    data class InPast(val unit: TimeUnit, val amount: Long) : ExpiryLabel
}
fun classifyExpiry(epochMs: Long, nowMs: Long): ExpiryLabel
```

Composable then maps `ExpiryLabel` to a `pluralStringResource`. This is
the only way to keep the unit pure JVM (no Android `Resources`), keep
plurals correct, and stay testable without Robolectric.

## 5. File layout

```
core/common/src/main/kotlin/com/firesin/xuipanel/core/common/format/
    PrettyBytes.kt
    PrettyExpiry.kt           // includes ExpiryLabel sealed + classifyExpiry
core/common/src/test/kotlin/.../format/
    PrettyBytesTest.kt
    PrettyExpiryTest.kt

core/xui/src/main/kotlin/com/firesin/xuipanel/core/xui/dto/
    OnlinesDto.kt             // OnlinesResponseDto
core/xui/src/main/kotlin/com/firesin/xuipanel/core/xui/
    XuiApi.kt                 // remove stale onlineClients(), add onlines()
    XuiClient.kt              // add fetchOnlines() facade
core/xui/src/test/kotlin/com/firesin/xuipanel/core/xui/dto/
    OnlinesDtoTest.kt         // parser test (success / failure / missing obj)

feature/stats/src/main/kotlin/com/firesin/xuipanel/feature/stats/
    StatsScreen.kt            // replaces stub
    StatsViewModel.kt
    StatsUiState.kt
    ui/                       // ServerSummaryCard, InboundCard, ClientRow, ExpiryText
        ServerSummaryCard.kt
        InboundCard.kt
        ClientRow.kt
        ExpiryText.kt         // ExpiryLabel → pluralStringResource
feature/stats/src/main/res/values/strings.xml
feature/stats/src/main/res/values/plurals.xml
feature/stats/src/test/kotlin/com/firesin/xuipanel/feature/stats/
    StatsViewModelTest.kt

feature/dashboard/src/main/kotlin/.../DashboardScreen.kt   // add Statistics → card
app/src/main/kotlin/.../navigation/XuiNavHost.kt           // wire onNavigateToStats
```

## 6. Entry point on Dashboard

In `DashboardContent`'s `StatusList`, append one item:

```kotlin
item { StatisticsLinkCard(onClick = onNavigateToStats) }
```

`StatisticsLinkCard` = a plain `Card` with title `"Statistics →"` and a
small caption `"Per-inbound traffic and online clients"`. No icon
required (matches Public IP card style). Visible only when `uiState is
Content` — same gating as the rest of dashboard cards (no point linking
to stats without an active panel).

`DashboardScreen` gets a new `onNavigateToStats: () -> Unit` parameter,
threaded through `dashboardGraph(...)` in `XuiNavHost`, identical to the
existing `onAddPanel` plumbing. No bottom-bar, no FAB, no overflow — per
the task.

## 7. Module / dependency impact

`:feature:stats/build.gradle.kts` gains:

```
implementation(project(":core:data"))
implementation(project(":core:xui"))
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.kotlinx.coroutines.android)
implementation(libs.androidx.material.icons.extended)   // expand/collapse chevrons
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
testOptions { unitTests.all { it.useJUnitPlatform() } }
```

**No new third-party libraries.** Vico, Coil, charting libs — none needed.
Material 3 already ships `LinearProgressIndicator` (used for the inbound
quota bar).

## 8. UI structure

Single `Scaffold` + `TopAppBar("Statistics")` + `PullToRefreshBox`
wrapping a `LazyColumn`:

```
ServerSummaryCard(summary)                          // sticky-ish, just first item
InboundCard(inbound, expanded, onlineEmails) ─┐
   header row: remark · protocol · port           │ tap → toggle
   up/down/total bytes                             │
   if total > 0: LinearProgressIndicator           │
   ExpiryText(inbound.expiryTime)                  │
   AnimatedVisibility(expanded):                   │
       Column { for c in clientStats:              │
           ClientRow(c, online = c.email in onlineEmails) }
                                                  ─┘
... one per inbound
```

`InboundCard` re-renders whole `clientStats` block on expand — small (≤
~50 clients per inbound) and avoids the LazyColumn-inside-LazyColumn
trap. If we ever see >200 clients we revisit, but that is a separate
release.

`ClientRow` columns: `email | up · down | total/limit | expiry · online?`.
Disabled clients (`enable = false`) get reduced alpha; online clients
get a small green dot. If `onlinesAvailable == false`, the dot is hidden
unconditionally.

## 9. Errors

- `inbounds` Failure → `StatsUiState.Error(domainError)` with full-screen
  retry, identical to `DashboardScreen` `ErrorState`.
- `onlines` Failure → no error UI; `onlinesAvailable = false`; an
  unobtrusive caption under the `ServerSummaryCard`: *"Online status
  unavailable"*. No red color, no retry — pull-to-refresh re-tries
  everything.
- `NoActivePanel` → reuse the dashboard "Add panel" empty state via a
  small composable copy; do **not** introduce a shared empty-state
  component in this MVP (avoid scope creep into `:core:designsystem`).

## 10. Tests

- `OnlinesDtoTest` (kotlinx-serialization JSON):
  - happy: `{"success":true,"obj":["a@b","c@d"]}` → list size 2.
  - empty: `{"success":true,"obj":[]}` → empty list.
  - missing-obj: `{"success":true}` → `obj == null` (defaults).
  - failure envelope: `{"success":false,"msg":"x"}` → success false,
    obj null.
- `PrettyBytesTest` table-driven: `0`, `1`, `1023`, `1024`, `1536`,
  `1_048_576`, `1_572_864`, `1_073_741_824`, `1_099_511_627_776`, plus
  rounding edge `1500` → `"1.5 KB"` and `1023L * 1024` → `"1023.0 KB"`.
- `PrettyExpiryTest` (against `classifyExpiry`):
  - `0L` → `Never`.
  - now+30s → `InFuture(SECONDS, 30)`.
  - now+90m → `InFuture(HOURS, 1)`.
  - now+25h → `InFuture(DAYS, 1)`.
  - now-3d → `InPast(DAYS, 3)`.
  - boundary now+86400000 → `InFuture(DAYS, 1)`.
- `StatsViewModelTest` (mockk + Turbine):
  - happy: both APIs ok → emits `Loading` then `Content` with merged
    summary + correct `onlineEmails`.
  - inbounds fail → `Error(domainError)`; `onlines` mock not asserted
    (cancellation racing acceptable).
  - onlines fail, inbounds ok → `Content` with `onlinesAvailable=false`
    and `onlineEmails=∅`.
  - no active panel → `NoActivePanel`.
  - `toggleExpanded(7)` flips membership in `Content.expandedIds`.

No instrumented tests — pure VM + JVM utilities.

## 11. Risks and trade-offs

| Risk | Mitigation |
|---|---|
| `onlines` endpoint missing on older 3x-ui (e.g. <2.3) → 404 maps to `DomainError.PanelResponse`. UI silently hides badges (already covered). | Acceptable; document min-version in user-facing notes when MVP-6 ships. |
| Counters from `clientStats[].up/down` may not match the inbound's `up/down` (the inbound counter is Xray-side, client stats are 3x-ui-side and lag a few seconds). UI shows both — visible mismatch could confuse users. | Add a one-liner in stats screen empty-help text: *"Counters refresh every server poll."*. No reconciliation logic. |
| `onlines` returns only emails — if two clients in different inbounds share an email (allowed by 3x-ui), we light both dots. | Accepted; matches the panel web UI behaviour. |
| Large per-client lists (rare but possible: SS subscription inbounds) cause layout cost on expand. | Defer; revisit if >200 clients is reported. |
| `prettyBytes` divergence from existing `formatBytes` may bite if someone ports stats UI elements back to dashboard. | Both live in different packages; named distinctly; lint not warranted yet. |
| Locale-aware `prettyExpiry` plurals: Russian has 3 forms (1, 2-4, 5+), English 2. Using `pluralStringResource` is correct only if `quantity` matches the unit displayed. | `classifyExpiry` returns the integer count + unit; the Composable feeds that exact integer to `pluralStringResource`. Verified pattern. |

## 12. For tech lead: top-level breakdown

- [ ] **Plumb `onlines` API**: add `OnlinesResponseDto`, replace stale
      `onlineClients()` in `XuiApi` with `onlines()`, add
      `XuiClient.fetchOnlines()`. Parser test.
- [ ] **Add format utilities** in `:core:common` (`prettyBytes`,
      `classifyExpiry` + `ExpiryLabel`). Table-driven tests.
- [ ] **`StatsViewModel` + `StatsUiState`** with parallel `async` fetch,
      `expandedIds` toggle, `isRefreshing` flow. mockk + Turbine tests.
- [ ] **`StatsScreen` + UI subcomponents**: `ServerSummaryCard`,
      `InboundCard` (with `AnimatedVisibility`), `ClientRow`,
      `ExpiryText`. PullToRefresh, error state, no-active-panel state.
- [ ] **Dashboard entry point**: add `StatisticsLinkCard`, thread
      `onNavigateToStats` through `DashboardScreen` → `dashboardGraph` →
      `XuiNavHost`.
- [ ] **Strings + plurals** in `feature/stats/res/values/{strings,plurals}.xml`.
      Russian + English.
- [ ] **Module wiring**: `feature/stats/build.gradle.kts` — add
      `:core:data`, `:core:xui`, mockk/turbine/coroutines-test, JUnit5
      platform.
