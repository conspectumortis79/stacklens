
package com.stacklens.application.service

import com.stacklens.application.port.inbound.jwt.JwtAnalysisUseCase
import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import java.time.Instant
import java.util.Base64

@ApplicationService
class JwtAnalysisService(
    private val jsonCodec: JsonReaderPort,
    private val i18n: I18nPort,
    private val clock: ClockPort,
) : JwtAnalysisUseCase {
    data class JwtAnalysisResult(
        val header: Map<String, Any?>,
        val payload: Map<String, Any?>,
        val algorithm: String?,
        val tokenType: String?,
        val issuer: String?,
        val subject: String?,
        val audience: String?,
        val expirationUtc: String?,
        val issuedAtUtc: String?,
        val notBeforeUtc: String?,
        val status: String,
        val timeRemaining: String?,
        val jwtId: String?,
    )

    override fun analyze(token: String): JwtAnalysisResult {
        val parts = token.trim().split(".")
        if (parts.size < 2) {
            throw IllegalArgumentException(i18n.t("jwt_analysis.be_invalid_format"))
        }

        val header = decodeJsonSegment(parts[0], i18n.t("jwt_analysis.be_header_decode_error"))
        val payload = decodeJsonSegment(parts[1], i18n.t("jwt_analysis.be_payload_decode_error"))

        val expEpoch = (payload["exp"] as? Number)?.toLong()
        val iatEpoch = (payload["iat"] as? Number)?.toLong()
        val nbfEpoch = (payload["nbf"] as? Number)?.toLong()

        val expiry = computeExpiry(expEpoch)
        val issuedAtUtc = formatInstant(iatEpoch)
        val notBeforeUtc = formatInstant(nbfEpoch)
        val audience = extractAudience(payload)
        val jwtId = (payload["jti"] as? String)?.takeIf { it.isNotBlank() }

        return buildResult(header, payload, expiry, issuedAtUtc, notBeforeUtc, audience, jwtId)
    }

    private fun decodeJsonSegment(
        base64Url: String,
        errorKey: String,
    ): Map<String, Any?> =
        try {
            parseBase64UrlJson(base64Url)
        } catch (e: Exception) {
            throw IllegalArgumentException(errorKey)
        }

    private data class Expiry(
        val status: String,
        val expirationUtc: String?,
        val timeRemaining: String?,
    )

    private fun computeExpiry(expEpoch: Long?): Expiry {
        if (expEpoch == null) {
            return Expiry(status = "UNLIMITED", expirationUtc = null, timeRemaining = null)
        }
        val expInstant = Instant.ofEpochSecond(expEpoch)
        val expirationUtc = expInstant.toString().replace("T", " ").replace("Z", " UTC")
        val diffSeconds = expInstant.epochSecond - clock.now().epochSecond
        return if (diffSeconds < 0) {
            Expiry(
                status = "EXPIRED",
                expirationUtc = expirationUtc,
                timeRemaining = i18n.t("jwt_analysis.be_already_expired"),
            )
        } else {
            val days = diffSeconds / 86400
            val hours = (diffSeconds % 86400) / 3600
            Expiry(
                status = "VALID",
                expirationUtc = expirationUtc,
                timeRemaining =
                    if (days > 0) {
                        i18n.t("jwt_analysis.be_time_days_hours", days, hours)
                    } else {
                        i18n.t("jwt_analysis.be_time_hours", hours)
                    },
            )
        }
    }

    private fun formatInstant(epochSeconds: Long?): String? =
        epochSeconds?.let {
            Instant
                .ofEpochSecond(it)
                .toString()
                .replace("T", " ")
                .replace("Z", " UTC")
        }

    private fun extractAudience(payload: Map<String, Any?>): String? =
        payload["aud"]?.let { aud ->
            when (aud) {
                is List<*> -> aud.joinToString(", ") { it?.toString() ?: "" }
                else -> aud.toString()
            }
        }

    private fun buildResult(
        header: Map<String, Any?>,
        payload: Map<String, Any?>,
        expiry: Expiry,
        issuedAtUtc: String?,
        notBeforeUtc: String?,
        audience: String?,
        jwtId: String?,
    ): JwtAnalysisResult =
        JwtAnalysisResult(
            header = header,
            payload = payload,
            algorithm = header["alg"] as? String,
            tokenType = header["typ"] as? String,
            issuer = payload["iss"] as? String,
            subject = payload["sub"] as? String,
            audience = audience,
            expirationUtc = expiry.expirationUtc,
            issuedAtUtc = issuedAtUtc,
            notBeforeUtc = notBeforeUtc,
            status = expiry.status,
            timeRemaining = expiry.timeRemaining,
            jwtId = jwtId,
        )

    internal fun parseBase64UrlJson(base64Url: String): Map<String, Any?> {
        val paddingNeeded = (4 - base64Url.length % 4) % 4
        val padded = base64Url + "=".repeat(paddingNeeded)
        val decoded = Base64.getUrlDecoder().decode(padded)
        return jsonCodec.decodeMap(String(decoded))
    }
}
