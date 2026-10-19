package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerCliProber
import com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort
import com.stacklens.application.port.out.docker.DockerSocketProbe
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.docker.DockerStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DockerAvailabilityAdapter(
    private val cliProber: DockerCliProber,
    private val socketProbe: DockerSocketProbe,
    private val sockets: DockerSocketDiscoveryPort,
    private val clock: ClockPort,
    @Value("\${stacklens.docker.probe-cache-ttl-ms:8000}")
    private val probeCacheTtlMs: Long = PROBE_CACHE_TTL_MS,
    @Value("\${stacklens.docker.probe-unavailable-cache-ttl-ms:20000}")
    private val probeUnavailableCacheTtlMs: Long = PROBE_UNAVAILABLE_CACHE_TTL_MS,
) : DockerAvailabilityPort {
    private val log = LoggerFactory.getLogger(DockerAvailabilityAdapter::class.java)

    @Volatile
    private var cachedStatus: DockerStatus? = null

    @Volatile
    private var cachedAt: Long = 0L

    private var lastAvailable: Boolean? = null

    override fun checkDockerAvailability(): DockerStatus {
        val now = clock.epochMillis()
        val cached = cachedStatus
        val cacheTtl = if (cached?.available == false) probeUnavailableCacheTtlMs else probeCacheTtlMs
        if (cached != null && now - cachedAt < cacheTtl) return cached

        val viaCli = cliProber.probe()
        if (viaCli.available) {
            rememberAvailable(viaCli)
            return viaCli
        }

        val socketResult = probeViaSockets()
        if (socketResult != null) {
            rememberAvailable(socketResult)
            return socketResult
        }

        val unavailable = buildUnavailableStatus(viaCli)
        rememberUnavailable(unavailable)
        return unavailable
    }

    private fun probeViaSockets(): DockerStatus? {
        for (sock in sockets.findAllDockerSockets()) {
            val probe = socketProbe.ping(sock)
            if (probe.available) return probe
        }
        return null
    }

    private fun buildUnavailableStatus(viaCli: DockerStatus): DockerStatus {
        val socketReasons = sockets.findAllDockerSockets().map { socketProbe.ping(it).reason }
        val combined = (listOf(viaCli.reason) + socketReasons).joinToString("; ")
        return DockerStatus(false, combined)
    }

    private fun rememberAvailable(result: DockerStatus) {
        if (lastAvailable != true) log.info("Docker available: {}", result.reason)
        lastAvailable = true
        cachedStatus = result
        cachedAt = clock.epochMillis()
    }

    private fun rememberUnavailable(result: DockerStatus) {
        if (lastAvailable != false) log.warn("Docker not reachable: {}", result.reason)
        lastAvailable = false
        cachedStatus = result
        cachedAt = clock.epochMillis()
    }

    companion object {
        const val PROBE_CACHE_TTL_MS = 8_000L
        const val PROBE_UNAVAILABLE_CACHE_TTL_MS = 20_000L
    }
}
