package com.firesin.xuipanel.core.xui.share

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.ClientsJson
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.Security
import com.firesin.xuipanel.core.xui.dto.StreamSettings
import com.firesin.xuipanel.core.xui.dto.StreamSettingsParser
import java.net.URLEncoder
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds a v2rayN-compatible share URI for a single client on a given inbound.
 *
 * Pure function — no I/O, no Android dependencies (uses [java.util.Base64]).
 * Returns [Result.Failure] for unsupported transports or protocols.
 *
 * Design: docs/architecture/share-config.md §4, §5
 */
object ClientUri {

    /**
     * Build a share URI.
     *
     * @param client   The parsed client configuration.
     * @param inbound  The parent inbound DTO (provides port, protocol, remark, streamSettings).
     * @param host     The panel host name or IP (derived from `Panel.baseUrl` by the caller).
     */
    fun build(
        client: ClientConfig,
        inbound: InboundDto,
        host: String,
    ): Result<String, ShareError> {
        // Hysteria does not use the standard stream transport model — its streamSettings
        // has network="hysteria" which the shared parser rejects as UnsupportedTransport.
        // Route it early so it parses TLS directly from the raw JSON.
        if (client is ClientConfig.Hysteria) {
            return buildHysteria(client, inbound, host)
        }

        val streamResult = StreamSettingsParser.parse(inbound.streamSettings)
        val stream = when (streamResult) {
            is Result.Success -> streamResult.data
            is Result.Failure -> return streamResult
        }

        // Unsupported transport → early failure (parser may return Unsupported variant for
        // networks that parse OK but can't be serialised to a link).
        if (stream is StreamSettings.Unsupported) {
            return Result.Failure(ShareError.UnsupportedTransport(stream.network))
        }

        return when (client) {
            is ClientConfig.Vless -> buildVless(client, inbound, host, stream)
            is ClientConfig.Vmess -> buildVmess(client, inbound, host, stream)
            is ClientConfig.Shadowsocks -> buildShadowsocks(client, inbound, host, stream)
            is ClientConfig.Trojan -> buildTrojan(client, inbound, host, stream)
            is ClientConfig.Hysteria -> error("unreachable — Hysteria is handled above")
        }
    }

    // ── VLESS ─────────────────────────────────────────────────────────────────

    /**
     * `vless://{uuid}@{host}:{port}?{params}#{remark}`
     *
     * upstream: sub/subService.go genVlessLink @main
     */
    private fun buildVless(
        client: ClientConfig.Vless,
        inbound: InboundDto,
        host: String,
        stream: StreamSettings,
    ): Result<String, ShareError> {
        val params = mutableMapOf<String, String>()
        params["type"] = networkName(stream)

        // upstream: reads inbound settings.encryption and includes it if present
        val encryption = parseInboundEncryption(inbound.settings)
        if (encryption != null) {
            params["encryption"] = encryption
        }

        applyNetworkParams(stream, params)

        // security + flow
        when (val sec = stream.security) {
            is Security.None -> params["security"] = "none"
            is Security.Tls -> {
                applyTlsParams(sec, params)
                // flow only applies when transport is TCP
                // upstream: sub/subService.go genVlessLink — flow gated on streamNetwork == "tcp" @main
                if (stream is StreamSettings.Tcp && client.flow.isNotEmpty()) {
                    params["flow"] = client.flow
                }
            }
            is Security.Reality -> {
                applyRealityParams(sec, params)
                if (stream is StreamSettings.Tcp && client.flow.isNotEmpty()) {
                    params["flow"] = client.flow
                }
            }
        }

        val remark = buildRemark(inbound.remark, client.email)
        val base = "vless://${client.id}@$host:${inbound.port}"
        return Result.Success(buildLinkWithParams(base, params, remark))
    }

    // ── VMESS ─────────────────────────────────────────────────────────────────

    /**
     * `vmess://{base64Std(JSON)}`
     *
     * JSON keys follow the v2rayN schema. Base64 is standard alphabet WITH padding,
     * no line wraps (Base64.NO_WRAP).
     *
     * upstream: sub/subService.go genVmessLink + buildVmessLink @main
     * upstream: sub/subService.go buildVmessLink uses json.MarshalIndent + base64.StdEncoding @main
     */
    private fun buildVmess(
        client: ClientConfig.Vmess,
        inbound: InboundDto,
        host: String,
        stream: StreamSettings,
    ): Result<String, ShareError> {
        val remark = buildRemark(inbound.remark, client.email)

        // Derive host/path/type defaults per transport
        val (netHost, netPath, typeStr) = deriveVmessNetFields(stream)
        val security = when (stream.security) {
            is Security.None -> "none"
            is Security.Tls -> "tls"
            is Security.Reality -> "reality"
        }

        // Build JSON map — ordering preserved via LinkedHashMap for reproducible tests
        val obj = linkedMapOf<String, Any>(
            "v" to "2",
            "ps" to remark,
            "add" to host,
            "port" to inbound.port,
            "id" to client.id,
            "aid" to "0",
            // upstream: reads clients[i].Security for scy — our model lacks this field,
            // so we hard-code "auto". upstream: sub/subService.go genVmessLink @main
            "scy" to "auto",
            "net" to networkName(stream),
            "type" to typeStr,
            "host" to netHost,
            "path" to netPath,
            "tls" to security,
        )

        if (stream.security is Security.Tls) {
            val tls = stream.security as Security.Tls
            tls.sni?.let { obj["sni"] = it }
            if (tls.alpn.isNotEmpty()) obj["alpn"] = tls.alpn.joinToString(",")
            tls.fingerprint?.let { obj["fp"] = it }
        }

        val jsonStr = buildJsonString(obj)
        // upstream uses base64.StdEncoding (with '=' padding, no line wraps) — sub/subService.go buildVmessLink @main
        val encoded = Base64.getEncoder().encodeToString(jsonStr.toByteArray(Charsets.UTF_8))
        return Result.Success("vmess://$encoded")
    }

    // ── Shadowsocks ───────────────────────────────────────────────────────────

    /**
     * `ss://{base64Std(method:password)}@{host}:{port}?{params}#{remark}`
     *
     * Userinfo is standard base64 (with padding) — upstream uses `base64.StdEncoding`.
     * For 2022-series ciphers (method starts with '2'): `method:inboundPassword:clientPassword`.
     *
     * upstream: sub/subService.go genShadowsocksLink @main
     * upstream uses base64.StdEncoding (standard with '=' padding) — diverges from design-doc
     * §4 which specified base64url-no-padding. Upstream is ground-truth. @main
     */
    private fun buildShadowsocks(
        client: ClientConfig.Shadowsocks,
        inbound: InboundDto,
        host: String,
        stream: StreamSettings,
    ): Result<String, ShareError> {
        val method = client.method
        val encPart = if (method.firstOrNull() == '2') {
            // SS-2022: needs the inbound-level password
            val inboundPassword = ClientsJson.parseInboundPassword(inbound.settings)
                ?: return Result.Failure(ShareError.MissingInboundPassword)
            "$method:$inboundPassword:${client.password}"
        } else {
            "$method:${client.password}"
        }

        // upstream uses base64.StdEncoding (with '=' padding) — sub/subService.go genShadowsocksLink @main
        val userinfo = Base64.getEncoder().encodeToString(encPart.toByteArray(Charsets.UTF_8))
        val params = mutableMapOf<String, String>()
        params["type"] = networkName(stream)
        applyNetworkParams(stream, params)
        if (stream.security is Security.Tls) {
            applyTlsParams(stream.security as Security.Tls, params)
        }

        val remark = buildRemark(inbound.remark, client.email)
        val base = "ss://$userinfo@$host:${inbound.port}"
        return Result.Success(buildLinkWithParams(base, params, remark))
    }

    // ── TROJAN ────────────────────────────────────────────────────────────────

    /**
     * `trojan://{password}@{host}:{port}?{params}#{remark}`
     *
     * Structure mirrors [buildVless]: same transport params, same TLS/Reality handling.
     * `flow` is only included for Reality + TCP (same upstream gate as VLESS).
     *
     * upstream: sub/subService.go genTrojanLink @main
     */
    private fun buildTrojan(
        client: ClientConfig.Trojan,
        inbound: InboundDto,
        host: String,
        stream: StreamSettings,
    ): Result<String, ShareError> {
        val params = mutableMapOf<String, String>()
        params["type"] = networkName(stream)

        applyNetworkParams(stream, params)

        when (val sec = stream.security) {
            is Security.None -> params["security"] = "none"
            is Security.Tls -> applyTlsParams(sec, params)
            is Security.Reality -> {
                applyRealityParams(sec, params)
                // upstream: genTrojanLink gates flow on Reality + TCP only
                if (stream is StreamSettings.Tcp && client.flow.isNotEmpty()) {
                    params["flow"] = client.flow
                }
            }
        }

        val remark = buildRemark(inbound.remark, client.email)
        val base = "trojan://${client.password}@$host:${inbound.port}"
        return Result.Success(buildLinkWithParams(base, params, remark))
    }

    // ── HYSTERIA ──────────────────────────────────────────────────────────────

    /**
     * `hysteria2://{auth}@{host}:{port}?{params}#{remark}`
     * (or `hysteria://` when `settings.version == 1`)
     *
     * Hysteria does not use the shared stream-transport model; this builder reads
     * TLS directly from `streamSettings` JSON.  `security=tls` is always set.
     *
     * Params (when present): `sni`, `alpn`, `fp`, `insecure=1`, `obfs`, `obfs-password`.
     *
     * upstream: sub/subService.go genHysteriaLink @main
     */
    private fun buildHysteria(
        client: ClientConfig.Hysteria,
        inbound: InboundDto,
        host: String,
    ): Result<String, ShareError> {
        val streamRoot: JsonObject = runCatching {
            lenientJson.parseToJsonElement(inbound.streamSettings).jsonObject
        }.getOrElse {
            return Result.Failure(ShareError.InvalidStreamSettings("malformed streamSettings"))
        }

        val params = mutableMapOf<String, String>()
        params["security"] = "tls"

        val tlsObj = streamRoot["tlsSettings"]?.jsonObject
        if (tlsObj != null) {
            val sni = tlsObj["serverName"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            sni?.let { params["sni"] = it }

            val alpn = tlsObj["alpn"]?.runCatching {
                jsonArray.map { it.jsonPrimitive.content }
            }?.getOrNull() ?: emptyList()
            if (alpn.isNotEmpty()) params["alpn"] = alpn.joinToString(",")

            val settingsObj = tlsObj["settings"]?.jsonObject
            val fp = settingsObj?.get("fingerprint")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            fp?.let { params["fp"] = it }

            val insecure = settingsObj?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull ?: false
            if (insecure) params["insecure"] = "1"
        }

        // Salamander obfs (Hysteria2 only) — mirrors upstream genHysteriaLink
        val finalMask = streamRoot["finalmask"]?.jsonObject
        if (finalMask != null) {
            val udpMasks = finalMask["udp"]?.runCatching { jsonArray }?.getOrNull()
            udpMasks?.forEach { maskEl ->
                val mask = maskEl.jsonObject
                if (mask["type"]?.jsonPrimitive?.content == "salamander") {
                    val pw = mask["settings"]?.jsonObject?.get("password")
                        ?.jsonPrimitive?.content
                    if (!pw.isNullOrEmpty()) {
                        params["obfs"] = "salamander"
                        params["obfs-password"] = pw
                        return@forEach
                    }
                }
            }
        }

        // Determine protocol version from inbound.settings ("version": 1 → hysteria, else hysteria2)
        val version = runCatching {
            lenientJson.parseToJsonElement(inbound.settings).jsonObject["version"]
                ?.jsonPrimitive?.content?.toIntOrNull() ?: 2
        }.getOrDefault(2)
        val scheme = if (version == 1) "hysteria" else "hysteria2"

        val remark = buildRemark(inbound.remark, client.email)
        val base = "$scheme://${client.auth}@$host:${inbound.port}"
        return Result.Success(buildLinkWithParams(base, params, remark))
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * upstream: sub/subService.go applyShareNetworkParams @main
     */
    private fun applyNetworkParams(stream: StreamSettings, params: MutableMap<String, String>) {
        when (stream) {
            is StreamSettings.Tcp -> {
                if (stream.headerType == "http") {
                    stream.path?.let { params["path"] = it }
                    stream.host?.let { params["host"] = it }
                    params["headerType"] = "http"
                }
            }
            is StreamSettings.Ws -> {
                stream.path?.let { params["path"] = it }
                stream.host?.let { params["host"] = it }
            }
            is StreamSettings.Grpc -> {
                params["serviceName"] = stream.serviceName
                stream.authority?.let { params["authority"] = it }
                if (stream.multiMode) params["mode"] = "multi"
            }
            is StreamSettings.HttpUpgrade -> {
                stream.path?.let { params["path"] = it }
                stream.host?.let { params["host"] = it }
            }
            is StreamSettings.XHttp -> {
                stream.path?.let { params["path"] = it }
                stream.host?.let { params["host"] = it }
                stream.mode?.let { params["mode"] = it }
            }
            is StreamSettings.Unsupported -> { /* unreachable — filtered above */ }
        }
    }

    /**
     * upstream: sub/subService.go applyShareTLSParams @main
     */
    private fun applyTlsParams(tls: Security.Tls, params: MutableMap<String, String>) {
        params["security"] = "tls"
        tls.sni?.let { params["sni"] = it }
        if (tls.alpn.isNotEmpty()) params["alpn"] = tls.alpn.joinToString(",")
        tls.fingerprint?.let { params["fp"] = it }
    }

    /**
     * upstream: sub/subService.go applyShareRealityParams @main
     * Note: `spx` (spiderX) and `pqv` are omitted per MVP-5 scope
     * (design-doc §4, Open questions #3).
     */
    private fun applyRealityParams(reality: Security.Reality, params: MutableMap<String, String>) {
        params["security"] = "reality"
        reality.sni?.let { params["sni"] = it }
        reality.pbk?.let { params["pbk"] = it }
        reality.sid?.let { params["sid"] = it }
        reality.fp?.let { params["fp"] = it }
    }

    /** Returns the canonical network string used in URI params. */
    private fun networkName(stream: StreamSettings): String = when (stream) {
        is StreamSettings.Tcp -> "tcp"
        is StreamSettings.Ws -> "ws"
        is StreamSettings.Grpc -> "grpc"
        is StreamSettings.HttpUpgrade -> "httpupgrade"
        is StreamSettings.XHttp -> "xhttp"
        is StreamSettings.Unsupported -> stream.network
    }

    /**
     * Derives the triple (host, path, typeStr) fields written into the VMESS JSON.
     *
     * upstream: sub/subService.go applyVmessNetworkParams @main
     */
    private fun deriveVmessNetFields(stream: StreamSettings): Triple<String, String, String> =
        when (stream) {
            is StreamSettings.Tcp -> {
                val typeStr = stream.headerType ?: "none"
                val path = if (stream.headerType == "http") stream.path.orEmpty() else ""
                val host = if (stream.headerType == "http") stream.host.orEmpty() else ""
                Triple(host, path, typeStr)
            }
            is StreamSettings.Ws -> Triple(stream.host.orEmpty(), stream.path.orEmpty(), "none")
            is StreamSettings.Grpc -> Triple(stream.authority.orEmpty(), stream.serviceName, "none")
            is StreamSettings.HttpUpgrade -> Triple(stream.host.orEmpty(), stream.path.orEmpty(), "none")
            is StreamSettings.XHttp -> Triple(stream.host.orEmpty(), stream.path.orEmpty(), "none")
            is StreamSettings.Unsupported -> Triple("", "", "none")
        }

    /**
     * Constructs `base?k=v&k=v#fragment` using [URLEncoder] for values.
     *
     * Go's `url.Values.Encode()` sorts params alphabetically; we mirror this behaviour
     * so generated links are deterministic and match upstream output.
     *
     * upstream: sub/subService.go buildLinkWithParams @main
     */
    private fun buildLinkWithParams(
        base: String,
        params: Map<String, String>,
        fragment: String,
    ): String {
        if (params.isEmpty() && fragment.isEmpty()) return base
        val query = params.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                "${enc(k)}=${enc(v)}"
            }
        val sb = StringBuilder(base)
        if (query.isNotEmpty()) sb.append('?').append(query)
        if (fragment.isNotEmpty()) sb.append('#').append(enc(fragment))
        return sb.toString()
    }

    /**
     * `remark = inbound.remark + "-" + email`
     *
     * Derived from upstream default remarkModel `"-ieo"`:
     * separator=`-`, order=inbound-remark then email.
     *
     * upstream: sub/subService.go genRemark / remarkModel default "-ieo" in setting.go @main
     */
    private fun buildRemark(inboundRemark: String, email: String): String = when {
        inboundRemark.isEmpty() -> email
        email.isEmpty() -> inboundRemark
        else -> "$inboundRemark-$email"
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * Parses `settings.encryption` from the inbound settings JSON.
     * Returns null if absent or blank.
     *
     * upstream: sub/subService.go genVlessLink reads settings["encryption"] @main
     */
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseInboundEncryption(settingsJson: String): String? = runCatching {
        val root = lenientJson.parseToJsonElement(settingsJson).jsonObject
        root["encryption"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Minimal JSON serialisation for the VMESS payload — avoids pulling in a full
     * serialisation framework just for this map.  Only handles String and Int values,
     * which covers the entire VMESS JSON schema.
     */
    private fun buildJsonString(obj: Map<String, Any>): String {
        val sb = StringBuilder("{")
        obj.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(k.jsonEscape()).append('"').append(':')
            when (v) {
                is String -> sb.append('"').append(v.jsonEscape()).append('"')
                is Int -> sb.append(v)
                else -> sb.append('"').append(v.toString().jsonEscape()).append('"')
            }
        }
        sb.append('}')
        return sb.toString()
    }

    private fun String.jsonEscape(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
