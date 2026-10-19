package com.stacklens.application.port.out.grafana

interface GrafanaConfigLookupPort {
    fun findConfig(id: Long): GrafanaConfigInfo?
}
