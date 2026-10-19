## grafana.analyseFormatHint
[Brief, clear analysis in English – max 4 paragraphs. Explain what should be monitored and which metrics are relevant.]

## grafana.rebuildSummaryHint
[2-4 sentences in English: What was changed and why.]

## grafana.chatInstruction
Always reply in the same language the user used. Be precise and practical. Refer concretely to the metrics and panels in the dashboard. Give clear recommendations – e.g. whether a metric is meaningful, how it can be captured better, or which alerts are advisable. If no dashboard JSON is present, answer the question generally as a Grafana expert.

## grafana._section.rebuild
Dashboard Rebuild

## grafana.rebuildPatch
You are a Grafana expert. Apply the user's change instructions to the following Grafana dashboard JSON exactly.

DASHBOARD JSON (original):
```json
{dashboardJson}
```

USER INSTRUCTIONS:
{instructions}

Respond EXCLUSIVELY with this JSON object in a ```json code block – no text before or after:

```json
{
  "summary": "2-4 sentences in English: What was changed and why.",
  "changes": ["Specific change 1", "Specific change 2"],
  "patch": {
    "modifiedPanels": [
      { "id": 1, "...": "complete panel JSON ONLY for changed panels – keep all original panel fields, only change the requested values" }
    ],
    "addPanels": [],
    "removePanelIds": [],
    "dashboardPatch": {}
  }
}
```

Important rules:
- "modifiedPanels" contains only the CHANGED panels as complete panel JSON (keep all original panel fields – only change the requested values)
- Do NOT include unchanged panels in "modifiedPanels" – they are automatically taken from the original
- Keep panel IDs from the original
- Only populate "addPanels" when new panels are added
- Only populate "removePanelIds" when panels are removed
- Only populate "dashboardPatch" when dashboard metadata (title, tags, time, etc.) is changed
- For color changes: fieldConfig.defaults.color, e.g. {"mode":"fixed","fixedColor":"purple"}
- The entire response JSON must be valid

## grafana._section.dummy
Dummy Data

## grafana.dummyData
You are a Grafana and monitoring expert. Analyze the dashboard JSON and generate realistic dummy time-series data for each panel for visualization.
Write all descriptive text values in the JSON in English.

DASHBOARD JSON:
```json
{jsonSnippet}
```

Respond EXCLUSIVELY with the following JSON (no additional text outside the JSON block):

```json
{
  "panels": [
    {
      "panelId": 1,
      "title": "CPU Usage",
      "type": "timeseries",
      "chartType": "line",
      "unit": "%",
      "labels": ["00:00","00:05","00:10","00:15","00:20","00:25","00:30","00:35","00:40","00:45","00:50","00:55","01:00","01:05","01:10","01:15","01:20","01:25","01:30","01:35"],
      "datasets": [
        { "label": "cpu_usage", "color": "#5794f2", "data": [42,48,51,45,60,55,49,53,58,47,52,44,61,57,50,46,55,63,48,52] }
      ]
    }
  ]
}
```

Rules:
- For panels of type timeseries/graph: chartType="line", 20 time points at 5-minute intervals (HH:MM format)
- For gauge/stat: chartType="doughnut", one data point (current percentage value 0-100)
- For bar/histogram/barchart: chartType="bar", 8-12 categories
- Skip row panels (type="row")
- Choose colors matching the metrics: CPU/Load=blue (#5794f2), Memory/RAM=purple (#b877d9), Error=red (#f2495c), OK/Success=green (#73bf69), Latency=orange (#ff9830), Disk=yellow (#fade2a)
- Realistic values: CPU 20-80%, RAM 40-85%, Requests 100-500/s, Latency 10-300ms, Error rate 0-5%
- If multiple datasets/metrics per panel: multiple objects in the datasets array with different colors

## grafana._section.chat
Chat Prompt

## grafana.chatPrompt
You are a monitoring and Grafana expert. The user has a Grafana dashboard and asks questions about the metrics, panels, and monitoring strategies used.
{jsonContext}
USER QUESTION:
{message}

Always reply in the same language the user used in their question. Be precise and practical. Refer concretely to the metrics and panels in the dashboard. Give clear recommendations – e.g. whether a metric is meaningful, how it can be captured better, or which alerts are advisable. If no dashboard JSON is present, answer the question generally as a Grafana expert.

## grafana._section.context
JSON Context

## grafana.jsonContextBlock

DASHBOARD JSON:
```json
{snippet}
```
