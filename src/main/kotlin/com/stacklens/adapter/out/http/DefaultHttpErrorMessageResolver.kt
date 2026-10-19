package com.stacklens.adapter.out.http

import com.stacklens.application.port.out.HttpErrorMessageResolverPort
import com.stacklens.application.port.out.HttpErrorMessageVariant
import com.stacklens.application.port.out.i18n.I18nPort
import org.springframework.stereotype.Component

@Component
class DefaultHttpErrorMessageResolver(
    private val i18n: I18nPort,
) : HttpErrorMessageResolverPort {
    override fun resolveMessage(
        statusCode: Int,
        endpoint: String,
        responseBody: String,
        variant: HttpErrorMessageVariant,
    ): String =
        when (statusCode) {
            401 ->
                when (variant) {
                    HttpErrorMessageVariant.GRAFANA -> i18n.t("config.be_grafana_auth_failed")
                    HttpErrorMessageVariant.GENERIC -> i18n.t("config.be_auth_failed_401")
                }
            403 ->
                when (variant) {
                    HttpErrorMessageVariant.GRAFANA -> i18n.t("config.be_grafana_access_denied")
                    HttpErrorMessageVariant.GENERIC -> i18n.t("config.be_access_denied_403")
                }
            404 -> i18n.t("config.be_endpoint_not_found_404", endpoint)
            else -> "HTTP $statusCode: ${responseBody.take(300)}"
        }
}
