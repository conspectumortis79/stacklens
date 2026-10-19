import { describe, it, expect, beforeEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { PROJECT_ROOT } from './_load.js';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';

describe('logging-fix stack-detection', () => {
  let dom, window;

  beforeEach(() => {
    dom = new JSDOM(
      `
      <!DOCTYPE html>
      <html lang="de">
      <body>
        <div id="lgResult" class="lg-result visible">
          <div id="lgSummaryBadges"></div>
        </div>
        <div id="lgLocalRepoList">
          <div class="lg-local-repo-card selected" data-repo-id="1" data-repo-path="/tmp/repo"></div>
        </div>
        <span id="lgRepoMismatchLogStacks"></span>
        <span id="lgRepoMismatchRepoStacks"></span>
        <div id="lgRepoMismatchWarn" style="display:none;"></div>
      </body>
      </html>
      `,
      { url: 'http://localhost/logging' },
    );
    window = dom.window;
    global.document = window.document;
    global.window = window;
    global.fetch = () => Promise.resolve({ ok: true, json: async () => ({ stacks: [], indicators: [] }) });

    const script = extractScriptContent(
      readFileSync(
        resolve(
          PROJECT_ROOT,
          'src/main/resources/templates/fragments/logging/scripts-stack-detection.html',
        ),
        'utf8',
      ),
    );
    window.eval(script);
  });

  it('detects Java + Spring from a Spring Boot stacktrace', () => {
    const log = `
      2025-01-15 ERROR com.shop.checkout.CheckoutService
      java.lang.NullPointerException
        at com.shop.checkout.CheckoutService.completeOrder(CheckoutService.kt:142)
      HikariPool-1 - Connection is not available
    `;
    const info = lgDetectLogStacks(log);
    expect(info.stacks).toContain('java');
    expect(info.stacks).toContain('kotlin');
    expect(info.stacks).toContain('spring');
  });

  it('detects Node.js from Express stacktrace', () => {
    const log = `
      Error: connect ECONNREFUSED 10.0.4.21:5432
        at TCPConnectWrap.afterConnect (node:net:1567:16)
        at /app/node_modules/undici/index.js:2841:31
    `;
    const info = lgDetectLogStacks(log);
    expect(info.stacks).toContain('node');
    expect(info.stacks).not.toContain('python');
  });

  it('detects Python from Django traceback', () => {
    const log = `
      Traceback (most recent call last):
        File "/app/views.py", line 42, in charge
          return self.process(request)
      ImportError: cannot import name 'pdfmetrics' from 'reportlab.pdfbase'
      django.request: Internal Server Error: /api/export/
    `;
    const info = lgDetectLogStacks(log);
    expect(info.stacks).toContain('python');
    expect(info.stacks).toContain('django');
    expect(info.stacks).not.toContain('java');
  });

  it('detects Kubernetes from container events', () => {
    const log = `
      2025-01-15 ERROR k8s.pod/payment-cleanup-1747 Container killed: OOMKilled
      CrashLoopBackOff: restarting too quickly
    `;
    const info = lgDetectLogStacks(log);
    expect(info.stacks).toContain('kubernetes');
  });

  it('returns match when both log and repo are Java/Spring', () => {
    expect(lgComputeMatch(['java', 'spring'], ['java'])).toBe('match');
    expect(lgComputeMatch(['java', 'spring'], ['kotlin', 'java'])).toBe('match');
    expect(lgComputeMatch(['kotlin'], ['java'])).toBe('match');
  });

  it('returns mismatch when log is Python but repo is Java', () => {
    expect(lgComputeMatch(['python', 'django'], ['java'])).toBe('mismatch');
    expect(lgComputeMatch(['node'], ['java'])).toBe('mismatch');
  });

  it('returns unknown when no stacks detected on either side', () => {
    expect(lgComputeMatch([], [])).toBe('unknown');
    expect(lgComputeMatch(['java'], [])).toBe('unknown');
    expect(lgComputeMatch([], ['java'])).toBe('unknown');
  });

  it('treats django as compatible with python', () => {
    expect(lgComputeMatch(['python'], ['django'])).toBe('match');
    expect(lgComputeMatch(['django'], ['python'])).toBe('match');
  });

  it('treats typescript as compatible with node', () => {
    expect(lgComputeMatch(['typescript'], ['node'])).toBe('match');
    expect(lgComputeMatch(['node'], ['typescript'])).toBe('match');
  });
});

function extractScriptContent(html) {
  const match = html.match(/<script[^>]*>([\s\S]*?)<\/script>/);
  if (!match) return '';
  let body = match[1];
  const names = new Set();
  for (const m of body.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) names.add(m[1]);
  for (const m of body.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=\s*function\b/gm)) names.add(m[1]);
  for (const name of names) {
    body += `\ntry { if (typeof ${name} !== 'undefined') window.${name} = ${name}; } catch (_) {}\n`;
  }
  return body;
}
