package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.xui.dto.ClientIpsResponseDto
import com.firesin.xuipanel.core.xui.dto.ClientSettingsBodyDto
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.LastOnlineResponseDto
import com.firesin.xuipanel.core.xui.dto.LoginRequestDto
import com.firesin.xuipanel.core.xui.dto.LoginResponseDto
import com.firesin.xuipanel.core.xui.dto.LogsResponseDto
import com.firesin.xuipanel.core.xui.dto.OnlinesResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.firesin.xuipanel.core.xui.dto.SetEnableRequestDto
import com.firesin.xuipanel.core.xui.dto.SubLinksResponseDto
import com.firesin.xuipanel.core.xui.dto.XrayLogsResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface XuiApi {

    /**
     * JSON login. Successful response sets the `3x-ui` session cookie.
     * `twoFactorCode` is required only when 2FA is enabled — omit otherwise.
     */
    @POST("login")
    suspend fun login(@Body body: LoginRequestDto): Response<LoginResponseDto>

    @GET("panel/api/inbounds/list")
    suspend fun listInbounds(): Response<InboundListResponseDto>

    @GET("panel/api/inbounds/get/{id}")
    suspend fun getInbound(@Path("id") id: Int): Response<InboundListResponseDto>

    @POST("panel/api/inbounds/del/{id}")
    suspend fun deleteInbound(@Path("id") id: Int): Response<LoginResponseDto>

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

    /** Add client(s) to an inbound. JSON body `{"id":<inboundId>,"settings":"<json>"}`. */
    @POST("panel/api/inbounds/addClient")
    suspend fun addClient(@Body body: ClientSettingsBodyDto): Response<LoginResponseDto>

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
}
