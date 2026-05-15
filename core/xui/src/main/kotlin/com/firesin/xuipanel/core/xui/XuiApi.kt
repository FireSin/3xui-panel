package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.xui.dto.ApiTokensListResponseDto
import com.firesin.xuipanel.core.xui.dto.CreateApiTokenRequestDto
import com.firesin.xuipanel.core.xui.dto.CreateApiTokenResponseDto
import com.firesin.xuipanel.core.xui.dto.PanelAllSettingsResponseDto
import com.firesin.xuipanel.core.xui.dto.SetApiTokenEnabledRequestDto
import com.firesin.xuipanel.core.xui.dto.UpdateUserRequestDto
import kotlinx.serialization.json.JsonObject
import com.firesin.xuipanel.core.xui.dto.AddCustomGeoRequestDto
import com.firesin.xuipanel.core.xui.dto.AddInboundRequestDto
import com.firesin.xuipanel.core.xui.dto.CopyClientsRequestDto
import com.firesin.xuipanel.core.xui.dto.CsrfTokenResponseDto
import com.firesin.xuipanel.core.xui.dto.ClientTrafficResponseDto
import com.firesin.xuipanel.core.xui.dto.TwoFactorResponseDto
import com.firesin.xuipanel.core.xui.dto.AddNodeRequestDto
import com.firesin.xuipanel.core.xui.dto.ClientIpsResponseDto
import com.firesin.xuipanel.core.xui.dto.ClientLinksResponseDto
import com.firesin.xuipanel.core.xui.dto.ClientSettingsBodyDto
import com.firesin.xuipanel.core.xui.dto.ConfigJsonResponseDto
import com.firesin.xuipanel.core.xui.dto.CustomGeoAliasesResponseDto
import com.firesin.xuipanel.core.xui.dto.EchCertResponseDto
import com.firesin.xuipanel.core.xui.dto.Mldsa65ResponseDto
import com.firesin.xuipanel.core.xui.dto.Mlkem768ResponseDto
import com.firesin.xuipanel.core.xui.dto.VlessEncResponseDto
import com.firesin.xuipanel.core.xui.dto.CustomGeoListResponseDto
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.LastOnlineResponseDto
import com.firesin.xuipanel.core.xui.dto.LoginRequestDto
import com.firesin.xuipanel.core.xui.dto.LoginResponseDto
import com.firesin.xuipanel.core.xui.dto.LogsResponseDto
import com.firesin.xuipanel.core.xui.dto.NewUuidResponseDto
import com.firesin.xuipanel.core.xui.dto.NewX25519ResponseDto
import com.firesin.xuipanel.core.xui.dto.NodeListResponseDto
import com.firesin.xuipanel.core.xui.dto.NodeResponseDto
import com.firesin.xuipanel.core.xui.dto.OnlinesResponseDto
import com.firesin.xuipanel.core.xui.dto.OutboundsTrafficResponseDto
import com.firesin.xuipanel.core.xui.dto.XrayResultResponseDto
import com.firesin.xuipanel.core.xui.dto.PanelSettingsResponseDto
import com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.firesin.xuipanel.core.xui.dto.SetEnableRequestDto
import com.firesin.xuipanel.core.xui.dto.SetNodeEnableRequestDto
import com.firesin.xuipanel.core.xui.dto.SubLinksResponseDto
import com.firesin.xuipanel.core.xui.dto.TestNodeResponseDto
import com.firesin.xuipanel.core.xui.dto.XrayLogsResponseDto
import com.firesin.xuipanel.core.xui.dto.XrayMetricsStateResponseDto
import com.firesin.xuipanel.core.xui.dto.XrayVersionResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface XuiApi {

    /**
     * JSON login. Successful response sets the `3x-ui` session cookie.
     * `twoFactorCode` is required only when 2FA is enabled — omit otherwise.
     */
    /**
     * Cookie-session login. Recent 3x-ui builds reject this POST unless the
     * `X-CSRF-Token` header is set — fetch it first via [csrfToken] (which doesn't
     * itself need a CSRF token), then pass the value here. Older panels accept
     * an empty header.
     */
    @POST("login")
    suspend fun login(
        @Header("X-CSRF-Token") csrfToken: String,
        @Body body: LoginRequestDto,
    ): Response<LoginResponseDto>

    /**
     * Open endpoint — no auth required. Returns whether the panel has 2FA (TOTP) enabled.
     * Call before login to decide whether to ask the user for an OTP.
     */
    /**
     * 2FA toggle probe — lives at the *root* `/getTwoFactorEnable`, NOT under
     * `panel/api/`. Bearer middleware doesn't cover it; recent forks gate the
     * call behind `X-CSRF-Token`. Empty header is accepted by older builds.
     */
    @POST("getTwoFactorEnable")
    suspend fun getTwoFactorEnable(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<TwoFactorResponseDto>

    @GET("panel/api/inbounds/list")
    suspend fun listInbounds(): Response<InboundListResponseDto>

    @GET("panel/api/inbounds/get/{id}")
    suspend fun getInbound(@Path("id") id: Int): Response<InboundListResponseDto>

    @POST("panel/api/inbounds/del/{id}")
    suspend fun deleteInbound(@Path("id") id: Int): Response<LoginResponseDto>

    /** Create a new inbound. Body holds three JSON-stringified blobs (settings/streamSettings/sniffing). */
    @POST("panel/api/inbounds/add")
    suspend fun addInbound(@Body body: AddInboundRequestDto): Response<LoginResponseDto>

    /** Replace an existing inbound. Same body shape as [addInbound]. */
    @POST("panel/api/inbounds/update/{id}")
    suspend fun updateInbound(
        @Path("id") id: Int,
        @Body body: AddInboundRequestDto,
    ): Response<LoginResponseDto>

    /** Fresh UUID v4 for client IDs. */
    @GET("panel/api/server/getNewUUID")
    suspend fun getNewUuid(): Response<NewUuidResponseDto>

    /** Fresh X25519 keypair for Reality settings. */
    @GET("panel/api/server/getNewX25519Cert")
    suspend fun getNewX25519Cert(): Response<NewX25519ResponseDto>

    /** ML-DSA-65 keypair (post-quantum signature). Returns `{seed, verify}`. */
    @GET("panel/api/server/getNewmldsa65")
    suspend fun getNewMldsa65(): Response<Mldsa65ResponseDto>

    /** ML-KEM-768 keypair (post-quantum KEM). Returns `{client, server}`. */
    @GET("panel/api/server/getNewmlkem768")
    suspend fun getNewMlkem768(): Response<Mlkem768ResponseDto>

    /** VLESS Encryption presets (X25519 / mlkem768x25519plus.native.{0rtt,600s}). */
    @GET("panel/api/server/getNewVlessEnc")
    suspend fun getNewVlessEnc(): Response<VlessEncResponseDto>

    /**
     * ECH (Encrypted Client Hello) keypair for the given SNI. Form body `sni=…`.
     * Returns `{echConfigList, echServerKeys}`.
     */
    @retrofit2.http.FormUrlEncoded
    @POST("panel/api/server/getNewEchCert")
    suspend fun getNewEchCert(
        @retrofit2.http.Field("sni") sni: String,
    ): Response<EchCertResponseDto>

    /**
     * Xray metrics state. When `enabled=false`, `reason` explains why (e.g. "metrics block
     * not configured in xray template"); when `enabled=true`, `snapshot` carries expvar values.
     */
    @GET("panel/api/server/xrayMetricsState")
    suspend fun getXrayMetricsState(): Response<XrayMetricsStateResponseDto>

    /**
     * Time-series history for one Xray metric. Metrics: xrAlloc, xrSys, xrHeapObjects,
     * xrNumGC, xrPauseNs. Bucket sizes: 2, 30, 60, 120, 180, 300 seconds.
     */
    @GET("panel/api/server/xrayMetricsHistory/{metric}/{bucket}")
    suspend fun getXrayMetricsHistory(
        @Path("metric") metric: String,
        @Path("bucket") bucket: Int,
    ): Response<ServerHistoryResponseDto>

    /** Observatory snapshot — list of per-outbound latency/health/lastProbe entries. */
    @GET("panel/api/server/xrayObservatory")
    suspend fun getXrayObservatory(): Response<com.firesin.xuipanel.core.xui.dto.XrayObservatoryResponseDto>

    /** Observatory history for a single outbound tag. Same {t,v} shape as serverHistory. */
    @GET("panel/api/server/xrayObservatoryHistory/{tag}/{bucket}")
    suspend fun getXrayObservatoryHistory(
        @Path("tag") tag: String,
        @Path("bucket") bucket: Int,
    ): Response<ServerHistoryResponseDto>

    /**
     * Toggle inbound enable. JSON body `{"enable": true|false}` per api.txt.
     * Response reuses the generic success/msg envelope (LoginResponseDto shape).
     */
    @POST("panel/api/inbounds/setEnable/{id}")
    suspend fun setInboundEnable(
        @Path("id") id: Int,
        @Body body: SetEnableRequestDto,
    ): Response<LoginResponseDto>

    @GET("panel/api/server/status")
    suspend fun serverStatus(): Response<ServerStatusResponseDto>

    /**
     * Time-series for one metric, ~6h window, aggregated to [bucket]-second buckets.
     * Metric: cpu | mem | swap | netIn | netOut | tcpCount | udpCount | load1 | online.
     * Bucket: 2, 30, 60, 120, 180, 300 (seconds).
     */
    @GET("panel/api/server/history/{metric}/{bucket}")
    suspend fun serverHistory(
        @Path("metric") metric: String,
        @Path("bucket") bucket: Int,
    ): Response<ServerHistoryResponseDto>

    /** Restart the Xray service. 3x-ui: POST /panel/api/server/restartXrayService. */
    @POST("panel/api/server/restartXrayService")
    suspend fun restartXrayService(): Response<LoginResponseDto>

    /** Stop the Xray service. 3x-ui: POST /panel/api/server/stopXrayService. */
    @POST("panel/api/server/stopXrayService")
    suspend fun stopXrayService(): Response<LoginResponseDto>

    /** Returns the last [count] lines of the panel log. */
    @POST("panel/api/server/logs/{count}")
    suspend fun panelLogs(@Path("count") count: Int): Response<LogsResponseDto>

    /** Returns the last [count] Xray access-log entries as structured records. */
    @POST("panel/api/server/xraylogs/{count}")
    suspend fun xrayLogs(@Path("count") count: Int): Response<XrayLogsResponseDto>

    @POST("panel/api/inbounds/onlines")
    suspend fun onlines(): Response<OnlinesResponseDto>

    /** Map of client email → last-seen unix timestamp (seconds). */
    @POST("panel/api/inbounds/lastOnline")
    suspend fun lastOnline(): Response<LastOnlineResponseDto>

    /** Delete every depleted/expired client in inbound [id]. Pass -1 to sweep all inbounds. */
    @POST("panel/api/inbounds/delDepletedClients/{id}")
    suspend fun delDepletedClients(@Path("id") id: Int): Response<LoginResponseDto>

    /** Reset upload + download counters for every client in inbound [id]. Destructive. */
    @POST("panel/api/inbounds/resetAllClientTraffics/{id}")
    suspend fun resetAllClientTraffics(@Path("id") id: Int): Response<LoginResponseDto>

    /** All protocol URLs (vless://, vmess://, …) for clients sharing the subscription id. */
    @GET("panel/api/inbounds/getSubLinks/{subId}")
    suspend fun getSubLinks(@Path("subId") subId: String): Response<SubLinksResponseDto>

    /**
     * Server-rendered share URLs for one client on one inbound.
     * Empty [ClientLinksResponseDto.obj] for protocols without a URL form
     * (socks/http/mixed/wireguard/dokodemo/tunnel).
     * Multiple entries when `streamSettings.externalProxy` is configured.
     */
    @GET("panel/api/inbounds/getClientLinks/{id}/{email}")
    suspend fun getClientLinks(
        @Path("id") inboundId: Int,
        @Path("email") email: String,
    ): Response<ClientLinksResponseDto>

    /** Add client(s) to an inbound. JSON body `{"id":<inboundId>,"settings":"<json>"}`. */
    @POST("panel/api/inbounds/addClient")
    suspend fun addClient(@Body body: ClientSettingsBodyDto): Response<LoginResponseDto>

    /**
     * Copy selected clients from [body.sourceInboundId] into the inbound identified by [id].
     * [body.targetInboundId] must equal [id] (server validates both).
     * api.txt lines 164–172.
     */
    @POST("panel/api/inbounds/{id}/copyClients")
    suspend fun copyClients(
        @Path("id") id: Int,
        @Body body: CopyClientsRequestDto,
    ): Response<LoginResponseDto>

    /**
     * Bulk-import inbounds from a form-encoded JSON blob.
     * api.txt line 220–225: POST /panel/api/inbounds/import, body is form field "data".
     * Assumption (api.txt is sparse): field name is "data", value is JSON-encoded inbound payload.
     */
    @POST("panel/api/inbounds/import")
    @Multipart
    suspend fun importInbounds(@Part("data") data: RequestBody): Response<LoginResponseDto>

    /** Update a single client. JSON body — see [ClientSettingsBodyDto]. */
    @POST("panel/api/inbounds/updateClient/{clientKey}")
    suspend fun updateClient(
        @Path("clientKey") clientKey: String,
        @Body body: ClientSettingsBodyDto,
    ): Response<LoginResponseDto>

    @POST("panel/api/inbounds/{inboundId}/delClient/{clientKey}")
    suspend fun deleteClient(
        @Path("inboundId") inboundId: Int,
        @Path("clientKey") clientKey: String,
    ): Response<LoginResponseDto>

    /** Delete a client identified by email rather than UUID. Two path params. */
    @POST("panel/api/inbounds/{inboundId}/delClientByEmail/{email}")
    suspend fun deleteClientByEmail(
        @Path("inboundId") inboundId: Int,
        @Path("email") email: String,
    ): Response<LoginResponseDto>

    /** Manually adjust client upload/download counters. Body `{upload, download}` in bytes. */
    @POST("panel/api/inbounds/updateClientTraffic/{email}")
    suspend fun updateClientTraffic(
        @Path("email") email: String,
        @Body body: com.firesin.xuipanel.core.xui.dto.UpdateClientTrafficRequestDto,
    ): Response<LoginResponseDto>

    @POST("panel/api/inbounds/{inboundId}/resetClientTraffic/{email}")
    suspend fun resetClientTraffic(
        @Path("inboundId") inboundId: Int,
        @Path("email") email: String,
    ): Response<LoginResponseDto>

    /** Returns recently observed IPs for the client. obj is either a list of strings or the literal "No IP Record". */
    @POST("panel/api/inbounds/clientIps/{email}")
    suspend fun clientIps(@Path("email") email: String): Response<ClientIpsResponseDto>

    /** Clears the recorded IP list for the client. */
    @POST("panel/api/inbounds/clearClientIps/{email}")
    suspend fun clearClientIps(@Path("email") email: String): Response<LoginResponseDto>

    /**
     * Traffic counters for a client identified by email.
     * api.txt line 85–91: GET /panel/api/inbounds/getClientTraffics/:email
     */
    @GET("panel/api/inbounds/getClientTraffics/{email}")
    suspend fun getClientTrafficsByEmail(
        @Path("email") email: String,
    ): Response<ClientTrafficResponseDto>

    /**
     * Traffic counters for a client identified by its numeric client-stats row id.
     * api.txt line 92–97: GET /panel/api/inbounds/getClientTrafficsById/:id
     */
    @GET("panel/api/inbounds/getClientTrafficsById/{id}")
    suspend fun getClientTrafficsById(
        @Path("id") id: String,
    ): Response<ClientTrafficResponseDto>

    @GET("panel/api/custom-geo/list")
    suspend fun listCustomGeo(): Response<CustomGeoListResponseDto>

    @GET("panel/api/custom-geo/aliases")
    suspend fun getCustomGeoAliases(): Response<CustomGeoAliasesResponseDto>

    @POST("panel/api/custom-geo/add")
    suspend fun addCustomGeo(@Body body: AddCustomGeoRequestDto): Response<LoginResponseDto>

    @POST("panel/api/custom-geo/update/{id}")
    suspend fun updateCustomGeo(
        @Path("id") id: Int,
        @Body body: AddCustomGeoRequestDto,
    ): Response<LoginResponseDto>

    @POST("panel/api/custom-geo/delete/{id}")
    suspend fun deleteCustomGeo(@Path("id") id: Int): Response<LoginResponseDto>

    @POST("panel/api/custom-geo/download/{id}")
    suspend fun downloadCustomGeo(@Path("id") id: Int): Response<LoginResponseDto>

    @POST("panel/api/custom-geo/update-all")
    suspend fun updateAllCustomGeo(): Response<LoginResponseDto>

    // ---- Nodes ----

    @GET("panel/api/nodes/list")
    suspend fun listNodes(): Response<NodeListResponseDto>

    @GET("panel/api/nodes/get/{id}")
    suspend fun getNode(@Path("id") id: Int): Response<NodeResponseDto>

    @POST("panel/api/nodes/add")
    suspend fun addNode(@Body body: AddNodeRequestDto): Response<NodeResponseDto>

    @POST("panel/api/nodes/update/{id}")
    suspend fun updateNode(
        @Path("id") id: Int,
        @Body body: AddNodeRequestDto,
    ): Response<LoginResponseDto>

    @POST("panel/api/nodes/del/{id}")
    suspend fun deleteNode(@Path("id") id: Int): Response<LoginResponseDto>

    @POST("panel/api/nodes/setEnable/{id}")
    suspend fun setNodeEnable(
        @Path("id") id: Int,
        @Body body: SetNodeEnableRequestDto,
    ): Response<LoginResponseDto>

    @POST("panel/api/nodes/test")
    suspend fun testNode(@Body body: AddNodeRequestDto): Response<TestNodeResponseDto>

    @POST("panel/api/nodes/probe/{id}")
    suspend fun probeNode(@Path("id") id: Int): Response<LoginResponseDto>

    /**
     * Time-series for one node metric, ~6h window, aggregated to [bucketSecs]-second buckets.
     * Metric: cpu | mem | netIn | netOut | latency | online.
     * Bucket: 2, 30, 60, 120, 180, 300 (seconds).
     * Same response shape as [serverHistory].
     */
    @GET("panel/api/nodes/history/{id}/{metric}/{bucket}")
    suspend fun getNodeHistory(
        @Path("id") nodeId: Int,
        @Path("metric") metric: String,
        @Path("bucket") bucketSecs: Int,
    ): Response<ServerHistoryResponseDto>

    // ---- Power-user system actions ----

    /**
     * Reset upload + download counters on every inbound. Destructive — all traffic
     * accounting history is lost. No request body, no path params (api.txt line 202).
     */
    @POST("panel/api/inbounds/resetAllTraffics")
    suspend fun resetAllTraffics(): Response<LoginResponseDto>

    /**
     * Self-update the panel to the latest version. The server restarts on success;
     * the response connection may be dropped before the body is read (api.txt line 363).
     */
    @POST("panel/api/server/updatePanel")
    suspend fun updatePanel(): Response<LoginResponseDto>

    /**
     * Download and install the specified Xray [version] tag (e.g. "v25.5.16" or "latest").
     * Long-running — the server may restart Xray before writing the response (api.txt line 356).
     */
    @POST("panel/api/server/installXray/{version}")
    suspend fun installXray(@Path("version") version: String): Response<LoginResponseDto>

    /**
     * Send a DB backup to every Telegram admin recipient configured on the panel.
     * GET with no body, no params (api.txt line 526).
     */
    @GET("panel/api/backuptotgbot")
    suspend fun backupToTgBot(): Response<LoginResponseDto>

    // ---- Bundle A: server info ----

    /**
     * Currently installed Xray binary version string, e.g. "v25.5.16".
     * api.txt line 312–313: GET /panel/api/server/getXrayVersion.
     */
    @GET("panel/api/server/getXrayVersion")
    suspend fun getXrayVersion(): Response<XrayVersionResponseDto>

    /**
     * Check if a newer 3x-ui panel release is available on GitHub.
     * api.txt line 316–317: GET /panel/api/server/getPanelUpdateInfo.
     * Returns `{success, obj: {currentVersion, latestVersion, isUpdatable}}`.
     */
    @GET("panel/api/server/getPanelUpdateInfo")
    suspend fun getPanelUpdateInfo(): Response<PanelUpdateInfoDto>

    // ---- Bundle B: backup / config ----

    /**
     * Stream the SQLite database file as a binary attachment (manual backup).
     * api.txt line 324–325: GET /panel/api/server/getDb.
     * Must use @Streaming to avoid buffering the whole file in memory.
     */
    @Streaming
    @GET("panel/api/server/getDb")
    suspend fun getDb(): Response<ResponseBody>

    /**
     * Restore the panel DB from an uploaded SQLite file.
     * api.txt line 397–399: POST /panel/api/server/importDB.
     * Multipart form, field name "db". Panel restarts after restore. Destructive.
     */
    @Multipart
    @POST("panel/api/server/importDB")
    suspend fun importDb(@Part db: MultipartBody.Part): Response<LoginResponseDto>

    /**
     * Refresh ALL built-in GeoIP/GeoSite data files.
     * api.txt line 367–368: POST /panel/api/server/updateGeofile.
     */
    @POST("panel/api/server/updateGeofile")
    suspend fun updateGeofile(): Response<LoginResponseDto>

    /**
     * Refresh a single built-in Geo file by filename (e.g. "geoip.dat", "geosite.dat").
     * api.txt line 371–372: POST /panel/api/server/updateGeofile/:fileName.
     */
    @POST("panel/api/server/updateGeofile/{fileName}")
    suspend fun updateGeofileByName(@Path("fileName") fileName: String): Response<LoginResponseDto>

    /**
     * Panel-level settings (sub URI, sub-clash URI, sub-JSON URI, …). Used by the share
     * screen to build subscription URLs as `subURI + client.subId`.
     *
     * NOTE: this endpoint is under `/panel/setting/` (no `/api`) — Bearer middleware does
     * not cover it, so it requires a *cookie* session **and** the `X-CSRF-Token` header.
     * Callers go through [XuiClient.fetchPanelSettings], which establishes the session
     * via `/login` and adds the CSRF header explicitly.
     */
    @POST("panel/setting/defaultSettings")
    suspend fun panelSettings(@Header("X-CSRF-Token") csrfToken: String): Response<PanelSettingsResponseDto>

    /** One-shot CSRF token mint. Stable per session; we re-fetch on each settings call. */
    @GET("csrf-token")
    suspend fun csrfToken(): Response<CsrfTokenResponseDto>

    /**
     * List all API tokens. Under `/panel/setting/` — requires cookie session + X-CSRF-Token,
     * Bearer middleware does NOT cover this path.
     */
    @GET("panel/setting/apiTokens")
    suspend fun listApiTokens(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<ApiTokensListResponseDto>

    /**
     * Create a new API token. Under `/panel/setting/` — requires cookie + X-CSRF-Token.
     * Returns `{success:false, msg:"a token with that name already exists"}` on name collision.
     */
    @POST("panel/setting/apiTokens/create")
    suspend fun createApiToken(
        @Header("X-CSRF-Token") csrfToken: String,
        @Body body: CreateApiTokenRequestDto,
    ): Response<CreateApiTokenResponseDto>

    /** Delete an API token by id. Under `/panel/setting/` — requires cookie + X-CSRF-Token. */
    @POST("panel/setting/apiTokens/delete/{id}")
    suspend fun deleteApiToken(
        @Header("X-CSRF-Token") csrfToken: String,
        @Path("id") id: Int,
    ): Response<LoginResponseDto>

    /** Enable or disable an API token. Under `/panel/setting/` — requires cookie + X-CSRF-Token. */
    @POST("panel/setting/apiTokens/setEnabled/{id}")
    suspend fun setApiTokenEnabled(
        @Header("X-CSRF-Token") csrfToken: String,
        @Path("id") id: Int,
        @Body body: SetApiTokenEnabledRequestDto,
    ): Response<LoginResponseDto>

    /** Full panel settings blob (~70 fields). Cookie + X-CSRF-Token. */
    @POST("panel/setting/all")
    suspend fun getAllSettings(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<PanelAllSettingsResponseDto>

    /**
     * Persist the entire settings blob. Body shape mirrors `getAllSettings`.obj — pass the
     * fetched JsonObject with only the desired fields replaced to avoid clobbering anything.
     */
    @POST("panel/setting/update")
    suspend fun updateAllSettings(
        @Header("X-CSRF-Token") csrfToken: String,
        @Body body: JsonObject,
    ): Response<LoginResponseDto>

    /** Rotate admin credentials. Server validates old pair before applying new one. */
    @POST("panel/setting/updateUser")
    suspend fun updateUser(
        @Header("X-CSRF-Token") csrfToken: String,
        @Body body: UpdateUserRequestDto,
    ): Response<LoginResponseDto>

    /** Restart the whole 3x-ui process (5-10s downtime). Connection drops immediately. */
    @POST("panel/setting/restartPanel")
    suspend fun restartPanel(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<LoginResponseDto>

    // ---- Outbounds (cookie+CSRF, under /panel/xray/) ----
    // NOTE: /panel/xray/ is NOT covered by Bearer middleware (returns 307 redirect),
    // so all calls go through cookie session + CSRF, like /panel/setting/.

    /** Per-outbound traffic stats. */
    @GET("panel/xray/getOutboundsTraffic")
    suspend fun getOutboundsTraffic(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<OutboundsTrafficResponseDto>

    /** Last Xray stdout/stderr — useful when an outbound config refuses to start. */
    @GET("panel/xray/getXrayResult")
    suspend fun getXrayResult(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<XrayResultResponseDto>

    /** Reset counters for a single outbound by tag. */
    @retrofit2.http.FormUrlEncoded
    @POST("panel/xray/resetOutboundsTraffic")
    suspend fun resetOutboundsTraffic(
        @Header("X-CSRF-Token") csrfToken: String,
        @retrofit2.http.Field("tag") tag: String,
    ): Response<LoginResponseDto>

    /** Xray config template + tag lists + outboundTestUrl. */
    @POST("panel/xray/")
    suspend fun getXrayTemplate(
        @Header("X-CSRF-Token") csrfToken: String,
    ): Response<com.firesin.xuipanel.core.xui.dto.XrayTemplateResponseDto>

    /**
     * Test an outbound. Form fields:
     *  - outbound (required): JSON string of a single outbound
     *  - allOutbounds (optional): JSON array for dialerProxy resolution
     *  - mode (optional): "tcp" for fast dial-only probe, empty/missing for full HTTP probe
     */
    @retrofit2.http.FormUrlEncoded
    @POST("panel/xray/testOutbound")
    suspend fun testOutbound(
        @Header("X-CSRF-Token") csrfToken: String,
        @retrofit2.http.Field("outbound") outbound: String,
        @retrofit2.http.Field("allOutbounds") allOutbounds: String? = null,
        @retrofit2.http.Field("mode") mode: String? = null,
    ): Response<com.firesin.xuipanel.core.xui.dto.TestOutboundResponseDto>

    /**
     * Cloudflare WARP control. Actions: `data | del | config | reg | license`. Form fields
     * are sent only for `reg` (privateKey, publicKey) and `license`.
     */
    @retrofit2.http.FormUrlEncoded
    @POST("panel/xray/warp/{action}")
    suspend fun warpAction(
        @Header("X-CSRF-Token") csrfToken: String,
        @Path("action") action: String,
        @retrofit2.http.Field("privateKey") privateKey: String? = null,
        @retrofit2.http.Field("publicKey") publicKey: String? = null,
        @retrofit2.http.Field("license") license: String? = null,
    ): Response<com.firesin.xuipanel.core.xui.dto.WarpNordResponseDto>

    /**
     * NordVPN control. Actions: `countries | servers | reg | setKey | data | del | config`.
     * Form fields: `countryId` for `servers`, `token` for `reg`, `key` for `setKey`.
     */
    @retrofit2.http.FormUrlEncoded
    @POST("panel/xray/nord/{action}")
    suspend fun nordAction(
        @Header("X-CSRF-Token") csrfToken: String,
        @Path("action") action: String,
        @retrofit2.http.Field("countryId") countryId: String? = null,
        @retrofit2.http.Field("token") token: String? = null,
        @retrofit2.http.Field("key") key: String? = null,
    ): Response<com.firesin.xuipanel.core.xui.dto.WarpNordResponseDto>

    /**
     * Return the raw Xray config JSON currently running on this host.
     * api.txt line 319–320: GET /panel/api/server/getConfigJson.
     */
    @GET("panel/api/server/getConfigJson")
    suspend fun getConfigJson(): Response<ConfigJsonResponseDto>
}
