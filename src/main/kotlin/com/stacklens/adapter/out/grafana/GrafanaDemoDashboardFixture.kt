package com.stacklens.adapter.out.grafana

import com.stacklens.application.port.out.grafana.GrafanaDemoDashboardPort
import org.springframework.stereotype.Component

@Component
class GrafanaDemoDashboardFixture : GrafanaDemoDashboardPort {
    override fun demoDashboardJson(): String =
        """
        {
          "dashboard": {
            "id": null,
            "uid": "demo-monitoring",
            "title": "Demo Monitoring Dashboard – server-node01",
            "tags": ["demo", "monitoring"],
            "panels": [
              {
                "id": 1,
                "type": "timeseries",
                "title": "CPU-Auslastung",
                "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
                "fieldConfig": {
                  "defaults": { "min": 0, "max": 100, "unit": "percent",
                    "color": { "mode": "fixed", "fixedColor": "green" },
                    "thresholds": { "steps": [
                      { "value": null, "color": "green" },
                      { "value": 70, "color": "yellow" },
                      { "value": 90, "color": "red" }
                    ]}
                  }
                },
                "targets": [{ "refId": "A", "expr": "node_cpu_usage_percent{job=\"node\",instance=\"server-node01\"}", "legendFormat": "CPU %" }]
              },
              {
                "id": 2,
                "type": "timeseries",
                "title": "Arbeitsspeicher",
                "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 },
                "fieldConfig": {
                  "defaults": { "min": 0, "max": 100, "unit": "percent",
                    "color": { "mode": "fixed", "fixedColor": "blue" },
                    "thresholds": { "steps": [
                      { "value": null, "color": "green" },
                      { "value": 75, "color": "yellow" },
                      { "value": 90, "color": "red" }
                    ]}
                  }
                },
                "targets": [{ "refId": "A", "expr": "node_memory_usage_percent{job=\"node\",instance=\"server-node01\"}", "legendFormat": "RAM %" }]
              },
              {
                "id": 3,
                "type": "timeseries",
                "title": "Festplatten-I/O",
                "gridPos": { "x": 0, "y": 8, "w": 12, "h": 8 },
                "fieldConfig": { "defaults": { "min": 0, "unit": "MBs" } },
                "targets": [
                  { "refId": "A", "expr": "node_disk_read_bytes_per_sec{job=\"node\",instance=\"server-node01\"}", "legendFormat": "Lesen MB/s" },
                  { "refId": "B", "expr": "node_disk_write_bytes_per_sec{job=\"node\",instance=\"server-node01\"}", "legendFormat": "Schreiben MB/s" }
                ]
              },
              {
                "id": 4,
                "type": "timeseries",
                "title": "Netzwerk-I/O (eth0)",
                "gridPos": { "x": 12, "y": 8, "w": 12, "h": 8 },
                "fieldConfig": { "defaults": { "min": 0, "unit": "mbits",
                    "color": { "mode": "fixed", "fixedColor": "purple" }
                  } },
                "targets": [
                  { "refId": "A", "expr": "node_network_receive_bits_per_sec{job=\"node\",device=\"eth0\"}", "legendFormat": "Eingehend Mb/s" },
                  { "refId": "B", "expr": "node_network_transmit_bits_per_sec{job=\"node\",device=\"eth0\"}", "legendFormat": "Ausgehend Mb/s" }
                ]
              },
              {
                "id": 5,
                "type": "stat",
                "title": "Festplattennutzung",
                "gridPos": { "x": 0, "y": 16, "w": 6, "h": 4 },
                "fieldConfig": { "defaults": { "min": 0, "max": 100, "unit": "percent",
                    "thresholds": { "steps": [
                      { "value": null, "color": "green" },
                      { "value": 70, "color": "yellow" },
                      { "value": 85, "color": "red" }
                    ]}
                  } },
                "targets": [{ "refId": "A", "expr": "node_filesystem_use_percent{job=\"node\",mountpoint=\"/\"}", "legendFormat": "% belegt" }]
              },
              {
                "id": 6,
                "type": "stat",
                "title": "System Load (1m)",
                "gridPos": { "x": 6, "y": 16, "w": 6, "h": 4 },
                "fieldConfig": { "defaults": { "min": 0, "unit": "short",
                    "thresholds": { "steps": [
                      { "value": null, "color": "green" },
                      { "value": 2, "color": "yellow" },
                      { "value": 5, "color": "red" }
                    ]}
                  } },
                "targets": [{ "refId": "A", "expr": "node_load1{job=\"node\",instance=\"server-node01\"}", "legendFormat": "Load Average" }]
              },
              {
                "id": 7,
                "type": "stat",
                "title": "TCP-Verbindungen",
                "gridPos": { "x": 12, "y": 16, "w": 6, "h": 4 },
                "fieldConfig": { "defaults": { "min": 0, "unit": "short",
                    "color": { "mode": "fixed", "fixedColor": "blue" }
                  } },
                "targets": [{ "refId": "A", "expr": "node_tcp_established{job=\"node\",instance=\"server-node01\"}", "legendFormat": "aktiv" }]
              },
              {
                "id": 8,
                "type": "stat",
                "title": "CPU-Temperatur",
                "gridPos": { "x": 18, "y": 16, "w": 6, "h": 4 },
                "fieldConfig": { "defaults": { "min": 0, "max": 100, "unit": "celsius",
                    "color": { "mode": "fixed", "fixedColor": "orange" },
                    "thresholds": { "steps": [
                      { "value": null, "color": "green" },
                      { "value": 60, "color": "yellow" },
                      { "value": 80, "color": "red" }
                    ]}
                  } },
                "targets": [{ "refId": "A", "expr": "node_hwmon_temp_celsius{job=\"node\",chip=\"coretemp\"}", "legendFormat": "°C" }]
              }
            ]
          }
        }
        """.trimIndent()
}
