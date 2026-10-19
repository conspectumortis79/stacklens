(function () {
  'use strict';

  var MAX_RENDER_LINES = 5000;

  var CATEGORIES = [
    { id: 'critical',   severity: 'critical', re: /\b(FATAL|CRITICAL|PANIC|EMERGENCY|ALERT)\b|unrecoverable|segfault|SIGSEGV/i },
    { id: 'oom',        severity: 'error',    re: /OutOfMemoryError|OOMKilled|Cannot allocate memory|allocation failed|Java heap space|metaspace/i },
    { id: 'exception',  severity: 'error',    re: /\b[A-Za-z$_][A-Za-z0-9$_]*(Exception|Error)\b|^\s*Caused by:|Traceback \(most recent call last\)|panic:/i },
    { id: 'stack',      severity: 'error',    re: /^\s*at\s+[\w.$(]|^\s*\.\.\.\s*\d+ more|^\s*File ".*", line\s*\d+/i },
    { id: 'http',       severity: 'error',    re: /(HTTP\/[0-9.]+|"status":|status=|status code|responded with|response (status|code)|code=|returned \d).{0,12}\b(400|401|403|404|408|429|500|502|503|504)\b/i },
    { id: 'connection', severity: 'error',    re: /connection (refused|reset|aborted|closed|error|time?d? out)|ECONNREFUSED|ECONNRESET|broken pipe|socket hang up/i },
    { id: 'database',   severity: 'error',    re: /dead ?lock|SQLException|could not serialize|connection pool exhausted|too many connections|deadlock detected/i },
    { id: 'timeout',    severity: 'warn',     re: /\btime?outs?\b|timed out|ETIMEDOUT|deadline exceeded/i },
    { id: 'error',      severity: 'error',    re: /\b(ERROR|SEVERE|ERR)\b/i },
    { id: 'warn',       severity: 'warn',     re: /\b(WARN|WARNING)\b/i },
    { id: 'deprecated', severity: 'warn',     re: /\bdeprecated\b|deprecation (warning|notice)/i }
  ];

  var SEV_RANK = { info: 0, warn: 1, error: 2, critical: 3 };

  function analyzeLine(text) {
    var categories = [];
    for (var i = 0; i < CATEGORIES.length; i++) {
      if (CATEGORIES[i].re.test(text)) categories.push(CATEGORIES[i].id);
    }
    var severity = 'info';
    for (var j = 0; j < categories.length; j++) {
      for (var k = 0; k < CATEGORIES.length; k++) {
        if (CATEGORIES[k].id === categories[j]) {
          if (SEV_RANK[CATEGORIES[k].severity] > SEV_RANK[severity]) severity = CATEGORIES[k].severity;
          break;
        }
      }
    }
    return { severity: severity, categories: categories };
  }

  function analyzeLogs(text) {
    var normalized = String(text === null || text === undefined ? '' : text).replace(/\r\n/g, '\n');
    var lines = normalized.split('\n');
    var bySeverity = { critical: 0, error: 0, warn: 0, info: 0 };
    var byCategory = {};
    var issueLines = 0;
    var out = [];
    for (var i = 0; i < lines.length; i++) {
      var r = analyzeLine(lines[i]);
      out.push({ no: i + 1, text: lines[i], severity: r.severity, categories: r.categories });
      if (r.severity !== 'info') issueLines++;
      bySeverity[r.severity]++;
      for (var c = 0; c < r.categories.length; c++) {
        byCategory[r.categories[c]] = (byCategory[r.categories[c]] || 0) + 1;
      }
    }
    return { totalLines: lines.length, issueLines: issueLines, bySeverity: bySeverity, byCategory: byCategory, lines: out, maxRenderLines: MAX_RENDER_LINES };
  }

  window.loggingLogScanner = {
    analyzeLogs: analyzeLogs,
    CATEGORIES: CATEGORIES,
    MAX_RENDER_LINES: MAX_RENDER_LINES
  };
})();
