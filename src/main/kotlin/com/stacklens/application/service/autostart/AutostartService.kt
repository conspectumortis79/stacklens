package com.stacklens.application.service.autostart

import com.stacklens.application.port.inbound.platform.AutostartChange
import com.stacklens.application.port.inbound.platform.AutostartStatus
import com.stacklens.application.port.inbound.platform.AutostartUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.AutostartStorageException
import com.stacklens.application.port.out.platform.AutostartStore
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.domain.autostart.AutostartMode

class AutostartService(
    private val platform: PlatformPort,
    private val stores: Map<OsType, AutostartStore>,
    private val i18n: I18nPort,
    private val serverPort: Int,
) : AutostartUseCase {
    override fun status(): AutostartStatus {
        val os = platform.detect().osType
        val store = stores[os] ?: return unsupportedStatus()
        val installed = store.isInstalled()
        return AutostartStatus(
            platform = os.wire,
            supported = true,
            enabled = installed,
            mode = if (installed) store.installedMode() else AutostartMode.DESKTOP,
            serverPort = serverPort,
        )
    }

    override fun enable(mode: AutostartMode): AutostartChange = mutate(mode) { it.install(mode) }

    override fun disable(): AutostartChange = mutate(AutostartMode.DESKTOP) { it.uninstall() }

    private fun mutate(
        appliedMode: AutostartMode,
        action: (AutostartStore) -> Unit,
    ): AutostartChange {
        val store = stores[platform.detect().osType] ?: return AutostartChange.Unsupported(unsupportedMessage())
        return try {
            action(store)
            AutostartChange.Applied(appliedMode)
        } catch (e: AutostartStorageException) {
            AutostartChange.Rejected(i18n.t(e.i18nKey, e.path))
        } catch (e: Exception) {
            AutostartChange.Rejected(e.message ?: i18n.t("common.be_unknown_error"))
        }
    }

    private fun unsupportedStatus(): AutostartStatus =
        AutostartStatus(
            platform = OsType.UNSUPPORTED.wire,
            supported = false,
            enabled = false,
            mode = AutostartMode.DESKTOP,
            serverPort = serverPort,
        )

    private fun unsupportedMessage(): String = i18n.t("config.autostart.be_unsupported_platform")
}
