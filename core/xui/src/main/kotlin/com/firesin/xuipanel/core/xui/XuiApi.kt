package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.LoginResponseDto
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
    @POST("/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<LoginResponseDto>

    @GET("/panel/api/inbounds/list")
    suspend fun listInbounds(): Response<InboundListResponseDto>

    @GET("/panel/api/inbounds/get/{id}")
    suspend fun getInbound(@Path("id") id: Int): Response<InboundListResponseDto>

    @POST("/panel/api/inbounds/del/{id}")
    suspend fun deleteInbound(@Path("id") id: Int): Response<LoginResponseDto>

    @POST("/server/status")
    suspend fun serverStatus(): Response<ServerStatusResponseDto>

    @POST("/panel/inbound/onlines")
    suspend fun onlineClients(): Response<LoginResponseDto>
}
