package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.LogsResponseDto
import com.firesin.xuipanel.core.xui.dto.LoginResponseDto
import com.firesin.xuipanel.core.xui.dto.OnlinesResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface XuiApi {

    /**
     * Form-encoded login. Successful response sets the `3x-ui` session cookie.
     */
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<LoginResponseDto>

    @GET("panel/api/inbounds/list")
    suspend fun listInbounds(): Response<InboundListResponseDto>

    @GET("panel/api/inbounds/get/{id}")
    suspend fun getInbound(@Path("id") id: Int): Response<InboundListResponseDto>

    @POST("panel/api/inbounds/del/{id}")
    suspend fun deleteInbound(@Path("id") id: Int): Response<LoginResponseDto>

    /**
     * Sets the inbound enable/disable state.
     * 3x-ui API: POST /panel/api/inbounds/setEnable/{id} with form-encoded `enable=true|false`.
     * Response reuses the generic success/msg envelope (LoginResponseDto shape).
     */
    @FormUrlEncoded
    @POST("panel/api/inbounds/setEnable/{id}")
    suspend fun setInboundEnable(
        @Path("id") id: Int,
        @Field("enable") enable: Boolean,
    ): Response<LoginResponseDto>

    @GET("panel/api/server/status")
    suspend fun serverStatus(): Response<ServerStatusResponseDto>

    /** Restart the Xray service. 3x-ui: POST /panel/api/server/restartXrayService. */
    @POST("panel/api/server/restartXrayService")
    suspend fun restartXrayService(): Response<LoginResponseDto>

    /** Stop the Xray service. 3x-ui: POST /panel/api/server/stopXrayService. */
    @POST("panel/api/server/stopXrayService")
    suspend fun stopXrayService(): Response<LoginResponseDto>

    /** Returns the last [count] lines of the panel log. */
    @POST("panel/api/server/logs/{count}")
    suspend fun panelLogs(@Path("count") count: Int): Response<LogsResponseDto>

    /** Returns the last [count] lines of the Xray log. */
    @POST("panel/api/server/xraylogs/{count}")
    suspend fun xrayLogs(@Path("count") count: Int): Response<LogsResponseDto>

    @POST("panel/api/inbounds/onlines")
    suspend fun onlines(): Response<OnlinesResponseDto>

    @FormUrlEncoded
    @POST("panel/api/inbounds/addClient")
    suspend fun addClient(
        @Field("id") inboundId: Int,
        @Field("settings") settings: String,
    ): Response<LoginResponseDto>

    @FormUrlEncoded
    @POST("panel/api/inbounds/updateClient/{clientKey}")
    suspend fun updateClient(
        @Path("clientKey") clientKey: String,
        @Field("id") inboundId: Int,
        @Field("settings") settings: String,
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
}
