package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardMetricsParserTest {
    private val parser = DashboardMetricsParser(ObjectMapper())

    @Test
    fun `extractMetrics returns empty list for empty dashboard`() {
        val result = parser.extractMetrics("""{"dashboard":{"panels":[]}}""")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractMetrics extracts metric from measurement field`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "CPU",
                    "type": "graph",
                    "id": 1,
                    "targets": [
                      {"refId": "A", "measurement": "cpu_usage", "legendFormat": "{{host}}"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("cpu_usage", result[0]["metric"])
        assertEquals("unknown", result[0]["datasourceType"])
        assertEquals("A", result[0]["refId"])
        assertEquals("{{host}}", result[0]["legendFormat"])
        assertEquals("CPU", result[0]["panelTitle"])
        assertEquals("graph", result[0]["panelType"])
        assertEquals(1, result[0]["panelId"])
    }

    @Test
    fun `extractMetrics extracts metric from expr field when measurement is blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Memory",
                    "type": "graph",
                    "id": 2,
                    "targets": [
                      {"refId": "A", "expr": "node_memory_MemTotal"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("node_memory_MemTotal", result[0]["metric"])
        assertEquals("node_memory_MemTotal", result[0]["query"])
    }

    @Test
    fun `extractMetrics extracts metric from metricName when measurement and expr are blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Disk",
                    "type": "graph",
                    "id": 3,
                    "targets": [
                      {"refId": "A", "metricName": "disk_usage"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("disk_usage", result[0]["metric"])
    }

    @Test
    fun `extractMetrics extracts metric from query when measurement expr and metricName are blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Queries",
                    "type": "graph",
                    "id": 4,
                    "targets": [
                      {"refId": "A", "query": "SELECT *\nFROM metrics"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("SELECT *", result[0]["metric"])
    }

    @Test
    fun `extractMetrics returns null entry (skips) when no metric source is present`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Empty",
                    "type": "graph",
                    "id": 5,
                    "targets": [
                      {"refId": "A"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractMetrics resolves datasource type from nested datasource object`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "PromQL",
                    "type": "graph",
                    "id": 6,
                    "targets": [
                      {"refId": "A", "expr": "up", "datasource": {"type": "prometheus", "uid": "abc"}}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("prometheus", result[0]["datasourceType"])
    }

    @Test
    fun `extractMetrics falls back to string datasource when nested type missing`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "StringDS",
                    "type": "graph",
                    "id": 7,
                    "targets": [
                      {"refId": "A", "expr": "up", "datasource": "prometheus"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("prometheus", result[0]["datasourceType"])
    }

    @Test
    fun `extractMetrics processes row panels with sub-panels`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Row",
                    "type": "row",
                    "id": 8,
                    "panels": [
                      {
                        "title": "SubPanel",
                        "type": "graph",
                        "id": 9,
                        "targets": [
                          {"refId": "A", "expr": "metric_a"}
                        ]
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("SubPanel", result[0]["panelTitle"])
        assertEquals("metric_a", result[0]["metric"])
    }

    @Test
    fun `extractMetrics row sub-panel inherits parent title when blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "Row",
                    "type": "row",
                    "id": 10,
                    "panels": [
                      {
                        "type": "graph",
                        "id": 11,
                        "targets": [
                          {"refId": "A", "expr": "metric_b"}
                        ]
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("Row", result[0]["panelTitle"])
    }

    @Test
    fun `extractMetrics uses rawQuery as fallback for query`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 12,
                    "targets": [
                      {"refId": "A", "rawQuery": "SELECT 1"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("SELECT 1", result[0]["metric"])
    }

    @Test
    fun `extractMetrics uses rawSql as fallback for query`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 13,
                    "targets": [
                      {"refId": "A", "rawSql": "SELECT 2"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("SELECT 2", result[0]["metric"])
    }

    @Test
    fun `extractMetrics sets query to null when all sources are blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 14,
                    "targets": [
                      {"refId": "A", "measurement": "metric_c"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals("metric_c", result[0]["metric"])
        assertNull(result[0]["query"])
    }

    @Test
    fun `extractMetrics sets refId to null when blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 15,
                    "targets": [
                      {"measurement": "m"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertNull(result[0]["refId"])
    }

    @Test
    fun `extractMetrics sets legendFormat to null when blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 16,
                    "targets": [
                      {"refId": "A", "measurement": "m"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertNull(result[0]["legendFormat"])
    }

    @Test
    fun `extractMetrics sets datasourceType to unknown when blank`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 17,
                    "targets": [
                      {"refId": "A", "measurement": "m"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals("unknown", result[0]["datasourceType"])
    }

    @Test
    fun `extractMetrics uses query when non-blank but all lines are whitespace`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "title": "T",
                    "type": "graph",
                    "id": 18,
                    "targets": [
                      {"refId": "A", "query": "  \n   \nSELECT *\nFROM x"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        val result = parser.extractMetrics(json)
        assertEquals(1, result.size)
        assertEquals("SELECT *", result[0]["metric"])
    }

    @Test
    fun `resolveDatasourceType handles missing datasource`() {
        val node = ObjectMapper().readTree("""{}""")
        assertEquals("", parser.resolveDatasourceType(node))
    }

    @Test
    fun `resolveDatasourceType handles nested datasource type`() {
        val node = ObjectMapper().readTree("""{"datasource":{"type":"prometheus"}}""")
        assertEquals("prometheus", parser.resolveDatasourceType(node))
    }
}
