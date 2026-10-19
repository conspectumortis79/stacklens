window.MrViolations = (function () {
  var FILE_RE = /^(File|Datei):\s*(.+?)\s*$/i;
  var LINE_RE = /^(Lines?|Zeilen?|Zeile):\s*(.+?)\s*$/i;
  var ORIGINAL_RE = /^(Original|Originale|Originalblock):\s*$/i;
  var SUGGESTION_RE = /^(Suggestion|Vorschlag|Empfehlung|Ersatz):\s*$/i;

  function parseLineValue(raw) {
    if (!raw) return { lineNumber: 0, lineRange: null, lineUncertain: true };
    var s = String(raw).trim();
    if (/^(unknown|unbekannt|\?|n\/?a)$/i.test(s)) {
      return { lineNumber: 0, lineRange: null, lineUncertain: true };
    }
    var rangeMatch = s.match(/^(\d+)\s*[-–]\s*(\d+)$/);
    if (rangeMatch) {
      var start = parseInt(rangeMatch[1], 10);
      var end = parseInt(rangeMatch[2], 10);
      if (!isNaN(start) && !isNaN(end) && end >= start) {
        return {
          lineNumber: start,
          lineRange: { start: start, end: end },
          lineUncertain: true,
        };
      }
    }
    var approxMatch = s.match(/^(?:ca\.?|about|approx\.?|~)\s*(\d+)$/i);
    if (approxMatch) {
      return {
        lineNumber: parseInt(approxMatch[1], 10),
        lineRange: null,
        lineUncertain: true,
      };
    }
    var first = parseInt(s, 10);
    if (isNaN(first) || first < 1) {
      return { lineNumber: 0, lineRange: null, lineUncertain: true };
    }
    return { lineNumber: first, lineRange: null, lineUncertain: false };
  }

  function parseFile(raw) {
    if (!raw) return '';
    return String(raw).trim().replace(/`/g, '');
  }

  function parse(markdown) {
    var violations = [];
    if (!markdown) return violations;
    var lines = String(markdown).split(/\r?\n/);
    var current = null;
    var collectMode = null;
    var collectBuffer = [];
    function flushCollect() {
      if (!current) return;
      var text = collectBuffer.join('\n').replace(/\s+$/, '');
      if (collectMode === 'original') current.original = text;
      else if (collectMode === 'suggestion') current.suggestedCode = text;
      collectMode = null;
      collectBuffer = [];
    }
    for (var i = 0; i < lines.length; i++) {
      var raw = lines[i];
      var trimmed = raw.trim();
      var startsWithRedX = trimmed.indexOf('❌') !== -1;
      var startsWithGreenCheck = trimmed.indexOf('✅') !== -1;
      var startsWithWarn = trimmed.indexOf('⚠️') !== -1;
      if (startsWithRedX && !startsWithGreenCheck && !startsWithWarn) {
        flushCollect();
        if (current && current.file) violations.push(current);
        var rule = trimmed
          .replace(/^[-\s]*/, '')
          .replace(/^❌\s*/, '')
          .replace(/\*\*/g, '')
          .trim();
        current = {
          rule: rule,
          file: '',
          lineNumber: 0,
          lineRange: null,
          lineUncertain: false,
          original: '',
          suggestedCode: '',
        };
        continue;
      }
      if (!current) continue;
      var fileMatch = trimmed.match(FILE_RE);
      if (fileMatch) {
        flushCollect();
        current.file = parseFile(fileMatch[2]);
        continue;
      }
      var lineMatch = trimmed.match(LINE_RE);
      if (lineMatch) {
        flushCollect();
        var parsed = parseLineValue(lineMatch[2]);
        current.lineNumber = parsed.lineNumber;
        current.lineRange = parsed.lineRange;
        current.lineUncertain = parsed.lineUncertain;
        continue;
      }
      if (ORIGINAL_RE.test(trimmed)) {
        flushCollect();
        collectMode = 'original';
        continue;
      }
      if (SUGGESTION_RE.test(trimmed)) {
        flushCollect();
        collectMode = 'suggestion';
        continue;
      }
      if (collectMode) collectBuffer.push(raw);
    }
    flushCollect();
    if (current && current.file) violations.push(current);
    return violations;
  }

  return { parse: parse, parseLineValue: parseLineValue };
})();
