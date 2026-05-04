package com.firesin.xuipanel.core.xui.dto

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.xui.share.ShareError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the raw `streamSettings` JSON string (stored in [InboundDto.streamSettings])
 * into a typed [StreamSettings] model.
 *
 * Approach mirrors [ClientsJson]: hand-written JSON tree walk via kotlinx-serialization,
 * no generated DTO, `ignoreUnknownKeys = true` so upstream schema evolution doesn't break us.
 *
 * Design: docs/architecture/share-config.md §3
 */
object StreamSettingsParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse the raw `streamSettings` blob.
     *
     * Returns [Result.Failure] with [ShareError.InvalidStreamSettings] on malformed JSON or
     * missing required `network` field.
     * Returns [Result.Failure] with [ShareError.UnsupportedTransport] for "kcp" and "http"
     * networks (represented internally as [StreamSettings.Unsupported] but immediately surfaced
     * as an error here for early rejection in the builder).
     *
     * upstream: sub/subService.go unmarshalStreamSettings + applyShareNetworkParams @main
     */
    fun parse(raw: String): Result<StreamSettings, ShareError> {
        // Never include parser exception messages — kotlinx.serialization may quote a fragment of the
        // input around the offending offset, which can contain Reality privateKey / mldsa65Seed.
        val root: JsonObject = runCatching {
            json.parseToJsonElement(raw).jsonObject
        }.getOrElse {
            return Result.Failure(ShareError.InvalidStreamSettings("malformed streamSettings"))
        }

        val network = root["network"]?.jsonPrimitive?.content
            ?: return Result.Failure(ShareError.InvalidStreamSettings("missing 'network' field"))

        val security = runCatching { parseSecurity(root) }.getOrElse {
            return Result.Failure(ShareError.InvalidStreamSettings("malformed security block"))
        }

        return when (network) {
            "tcp" -> Result.Success(parseTcp(root, security))
            "ws" -> Result.Success(parseWs(root, security))
            "grpc" -> Result.Success(parseGrpc(root, security))
            "httpupgrade" -> Result.Success(parseHttpUpgrade(root, security))
            "xhttp" -> Result.Success(parseXHttp(root, security))
            "kcp", "http" -> Result.Failure(ShareError.UnsupportedTransport(network))
            else -> Result.Failure(ShareError.UnsupportedTransport(network))
        }
    }

    // ── Security layer ────────────────────────────────────────────────────────

    /**
     * upstream: sub/subService.go applyShareTLSParams / applyShareRealityParams @main
     */
    private fun parseSecurity(root: JsonObject): Security {
        return when (root["security"]?.jsonPrimitive?.content) {
            "tls" -> parseTls(root)
            "reality" -> parseReality(root)
            else -> Security.None
        }
    }

    /**
     * TLS: reads `tlsSettings.serverName`, `tlsSettings.alpn[]`,
     * `tlsSettings.settings.fingerprint`.
     *
     * upstream: sub/subService.go applyShareTLSParams @main
     */
    private fun parseTls(root: JsonObject): Security.Tls {
        val tlsObj = root["tlsSettings"]?.jsonObject ?: return Security.Tls(null, emptyList(), null)

        val sni = tlsObj["serverName"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val alpn = tlsObj["alpn"]?.runCatching { jsonArray.map { it.jsonPrimitive.content } }
            ?.getOrNull() ?: emptyList()

        // fingerprint is nested under tlsSettings.settings.fingerprint
        val settingsObj = tlsObj["settings"]?.jsonObject
        val fp = settingsObj?.get("fingerprint")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

        return Security.Tls(sni = sni, alpn = alpn, fingerprint = fp)
    }

    /**
     * Reality: reads public fields only.
     *
     * - `sni`  → `realitySettings.serverNames[0]`  (index 0; upstream picks random — divergence noted)
     * - `pbk`  → `realitySettings.settings.publicKey`
     * - `sid`  → `realitySettings.shortIds[0]`     (index 0; upstream picks random — divergence noted)
     * - `fp`   → `realitySettings.settings.fingerprint`
     *
     * Private fields (`privateKey`, `mldsa65Seed`, `mldsa65Verify`) are NOT included in the
     * data model and are silently dropped — this is the point of using `ignoreUnknownKeys = true`.
     *
     * upstream: sub/subService.go applyShareRealityParams @main
     */
    private fun parseReality(root: JsonObject): Security.Reality {
        val rObj = root["realitySettings"]?.jsonObject ?: return Security.Reality(null, null, null, null)

        val sni = rObj["serverNames"]?.runCatching {
            jsonArray.firstOrNull()?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        }?.getOrNull()

        val sid = rObj["shortIds"]?.runCatching {
            jsonArray.firstOrNull()?.jsonPrimitive?.content
            // upstream picks random index; we always pick index 0 for determinism
        }?.getOrNull()

        val settingsObj = rObj["settings"]?.jsonObject
        val pbk = settingsObj?.get("publicKey")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val fp = settingsObj?.get("fingerprint")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

        return Security.Reality(sni = sni, pbk = pbk, sid = sid, fp = fp)
    }

    // ── Network transports ────────────────────────────────────────────────────

    /**
     * upstream: sub/subService.go applyShareNetworkParams case "tcp" @main
     */
    private fun parseTcp(root: JsonObject, security: Security): StreamSettings.Tcp {
        val tcpObj = root["tcpSettings"]?.jsonObject
        val header = tcpObj?.get("header")?.jsonObject
        val typeStr = header?.get("type")?.jsonPrimitive?.content

        var path: String? = null
        var host: String? = null

        if (typeStr == "http") {
            val request = header?.get("request")?.jsonObject
            val requestPaths = request?.get("path")?.runCatching { jsonArray }?.getOrNull()
            path = requestPaths?.firstOrNull()?.jsonPrimitive?.content
            val headers = request?.get("headers")?.jsonObject
            host = searchHost(headers)
        }

        return StreamSettings.Tcp(
            headerType = typeStr?.takeIf { it == "http" },
            path = path,
            host = host,
            security = security,
        )
    }

    /**
     * upstream: sub/subService.go applyPathAndHostParams for "ws" @main
     */
    private fun parseWs(root: JsonObject, security: Security): StreamSettings.Ws {
        val wsObj = root["wsSettings"]?.jsonObject
        return StreamSettings.Ws(
            path = wsObj?.get("path")?.jsonPrimitive?.content,
            host = resolveHost(wsObj),
            security = security,
        )
    }

    /**
     * upstream: sub/subService.go applyShareNetworkParams case "grpc" @main
     */
    private fun parseGrpc(root: JsonObject, security: Security): StreamSettings.Grpc {
        val grpcObj = root["grpcSettings"]?.jsonObject
        return StreamSettings.Grpc(
            serviceName = grpcObj?.get("serviceName")?.jsonPrimitive?.content.orEmpty(),
            authority = grpcObj?.get("authority")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            multiMode = grpcObj?.get("multiMode")?.jsonPrimitive?.booleanOrNull ?: false,
            security = security,
        )
    }

    /**
     * upstream: sub/subService.go applyPathAndHostParams for "httpupgrade" @main
     */
    private fun parseHttpUpgrade(root: JsonObject, security: Security): StreamSettings.HttpUpgrade {
        val obj = root["httpupgradeSettings"]?.jsonObject
        return StreamSettings.HttpUpgrade(
            path = obj?.get("path")?.jsonPrimitive?.content,
            host = resolveHost(obj),
            security = security,
        )
    }

    /**
     * upstream: sub/subService.go applyShareNetworkParams case "xhttp" @main
     */
    private fun parseXHttp(root: JsonObject, security: Security): StreamSettings.XHttp {
        val obj = root["xhttpSettings"]?.jsonObject
        return StreamSettings.XHttp(
            path = obj?.get("path")?.jsonPrimitive?.content,
            host = resolveHost(obj),
            mode = obj?.get("mode")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            security = security,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the effective `host` from either the top-level `host` field or the
     * `headers.Host` sub-object — mirrors upstream `applyPathAndHostParams`.
     *
     * upstream: sub/subService.go applyPathAndHostParams + searchHost @main
     */
    private fun resolveHost(obj: JsonObject?): String? {
        if (obj == null) return null
        val direct = obj["host"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        if (direct != null) return direct
        val headers = obj["headers"]?.jsonObject
        return searchHost(headers)
    }

    /**
     * Searches `headers` map case-insensitively for a "Host" key, returning the first
     * string value or first element of an array value.
     *
     * upstream: sub/subService.go searchHost @main
     */
    private fun searchHost(headers: JsonObject?): String? {
        if (headers == null) return null
        for ((k, v) in headers) {
            if (k.equals("host", ignoreCase = true)) {
                // value may be a string or an array of strings
                v.runCatching { jsonArray.firstOrNull()?.jsonPrimitive?.content }
                    .getOrNull()?.let { return it }
                return v.jsonPrimitive.content
            }
        }
        return null
    }
}
