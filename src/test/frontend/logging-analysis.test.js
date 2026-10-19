import { describe, it, expect, beforeAll } from 'vitest';
import { loadScript, installBrowserStubs } from './_load.js';

installBrowserStubs();

const SAMPLE_LOG = [
  '2025-01-15 10:00:01.101 INFO  com.example.app.Main - Application started',
  '2025-01-15 10:00:02.202 WARN  com.example.app.Cache - Cache entry missing for key=42, will reload',
  '2025-01-15 10:00:03.303 ERROR com.example.app.OrderService - Failed to save order id=99',
  'java.sql.SQLException: Connection refused',
  '    at com.example.app.OrderService.save(OrderService.java:87)',
  '    at com.example.app.Main.run(Main.java:12)',
  'Caused by: java.net.ConnectException: Connection refused',
  '2025-01-15 10:00:04.404 INFO  com.example.app.Health - Health check OK, took 15 ms',
  '2025-01-15 10:00:05.505 WARN  com.example.app.Legacy - Deprecated API /v1/orders is deprecated',
  '2025-01-15 10:00:06.606 ERROR com.example.app.Http - Responded with 500 for GET /api/orders',
  '2025-01-15 10:00:07.707 ERROR com.example.app.Db - Deadlock detected on table orders',
  '2025-01-15 10:00:08.808 ERROR com.example.app.Pool - java.lang.OutOfMemoryError: Java heap space',
  '2025-01-15 10:00:09.909 INFO  com.example.app.Gc - GC pause 210 ms',
  '2025-01-15 10:00:10.010 INFO  com.example.app.Upstream - Waiting for upstream response',
  '2025-01-15 10:00:11.111 FATAL com.example.app.Main - Unrecoverable state, shutting down',
].join('\n');

describe('logging-analysis scanner', () => {
  beforeAll(() => {
    loadScript(window, 'js/logging-analysis.js');
  });

  function analyze(text) {
    return window.loggingLogScanner.analyzeLogs(text);
  }

  it('exposes the scanner on window', () => {
    expect(window.loggingLogScanner).toBeTruthy();
    expect(typeof window.loggingLogScanner.analyzeLogs).toBe('function');
    expect(window.loggingLogScanner.MAX_RENDER_LINES).toBe(5000);
  });

  it('returns zero issues for an empty text', () => {
    const r = analyze('');
    expect(r.totalLines).toBe(1);
    expect(r.issueLines).toBe(0);
    expect(r.bySeverity).toEqual({ critical: 0, error: 0, warn: 0, info: 1 });
    expect(Object.keys(r.byCategory)).toHaveLength(0);
  });

  it('treats null and undefined input as empty text', () => {
    expect(analyze(null).issueLines).toBe(0);
    expect(analyze(undefined).totalLines).toBe(1);
  });

  it('classifies a mixed log by category and severity', () => {
    const r = analyze(SAMPLE_LOG);
    expect(r.totalLines).toBe(15);
    expect(r.issueLines).toBe(11);
    expect(r.bySeverity.critical).toBe(1);
    expect(r.bySeverity.error).toBe(8);
    expect(r.bySeverity.warn).toBe(2);
    expect(r.bySeverity.info).toBe(4);
    expect(r.byCategory.exception).toBe(3);
    expect(r.byCategory.stack).toBe(2);
    expect(r.byCategory.connection).toBe(2);
    expect(r.byCategory.http).toBe(1);
    expect(r.byCategory.database).toBe(2);
    expect(r.byCategory.oom).toBe(1);
    expect(r.byCategory.deprecated).toBe(1);
  });

  it('ranks CRITICAL above ERROR on the same line', () => {
    const r = analyze('2025-01-15 ERROR app - FATAL: unrecoverable database failure');
    expect(r.lines[0].severity).toBe('critical');
    expect(r.lines[0].categories).toContain('critical');
    expect(r.lines[0].categories).toContain('error');
  });

  it('marks stack trace lines as errors even without keywords', () => {
    const r = analyze('    at com.example.Foo.bar(Foo.java:12)\n... 4 more');
    expect(r.lines[0].severity).toBe('error');
    expect(r.lines[0].categories).toContain('stack');
    expect(r.lines[1].severity).toBe('error');
    expect(r.lines[1].categories).toContain('stack');
  });

  it('detects Python tracebacks and tracebacks-only errors', () => {
    const r = analyze('Traceback (most recent call last):\n  File "app.py", line 3, in <module>\nValueError: bad value');
    expect(r.lines[0].categories).toContain('exception');
    expect(r.lines[1].categories).toContain('stack');
    expect(r.lines[2].categories).toContain('exception');
  });

  it('marks a clean INFO-only log as issue-free', () => {
    const r = analyze('INFO started\nINFO request completed in 3 ms\nINFO stopped');
    expect(r.issueLines).toBe(0);
    r.lines.forEach((l) => expect(l.severity).toBe('info'));
  });

  it('normalizes CRLF line endings', () => {
    const r = analyze('ERROR one\r\nWARN two\r\nINFO three');
    expect(r.totalLines).toBe(3);
    expect(r.issueLines).toBe(2);
  });

  it('does not mark a bare number as an HTTP error', () => {
    const r = analyze('INFO counter reached 500');
    expect(r.byCategory.http).toBeUndefined();
  });

  it('detects HTTP status codes only with HTTP context', () => {
    const r = analyze('INFO GET /api/orders -> HTTP/1.1 404 Not Found');
    expect(r.lines[0].categories).toContain('http');
  });

  it('keeps line numbers 1-based and text intact', () => {
    const r = analyze('a\nb\nERROR c');
    expect(r.lines[0].no).toBe(1);
    expect(r.lines[2].no).toBe(3);
    expect(r.lines[2].text).toBe('ERROR c');
  });
});
