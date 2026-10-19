import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { readFileSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BADGE = resolve(
  __dirname,
  '../../main/resources/static/js/mcp-status-badge.js',
);
const SOURCE = readFileSync(BADGE, 'utf8');

describe('Code-Analyse: MCP page-header badge must poll /api/mcp/status', () => {
  it('declares a setInterval-driven poll (no polling → no live update)', () => {
    expect(SOURCE).toMatch(/setInterval\s*\(/);
  });

  it('polls the same endpoint the manual status badge reads', () => {
    expect(SOURCE).toMatch(/\/api\/mcp\/status/);
    expect(SOURCE).toMatch(/POLL_MS\s*=\s*10000/);
  });

  it('avoids re-rendering when nothing changed (no per-tick DOM thrash)', () => {
    expect(SOURCE).toMatch(/_lastSignature/);
    expect(SOURCE).toMatch(/_maybeRender/);
  });

  it('tears down the poll timer when the page has no MCP-aware sources', () => {
    expect(SOURCE).toMatch(/_stopPolling\s*\(/);
    expect(SOURCE).toMatch(/_startPolling\s*\(/);
  });

  it('does NOT regress into the buggy "render-once-on-init" form', () => {
    const renderChipsCall = SOURCE.match(/^[^\n]*\brenderChips\s*\(\s*host,\s*status,\s*sources\s*\)/gm) || [];
    const calls = renderChipsCall.filter(line => !/^\s*function\s+renderChips\b/.test(line));
    expect(calls).toHaveLength(1);
    const callLine = SOURCE.split('\n').findIndex(l => /^\s*renderChips\s*\(\s*host,\s*status,\s*sources\s*\)/.test(l));
    const startLine = SOURCE.split('\n').findIndex(l => /function _maybeRender/.test(l));
    expect(startLine).toBeGreaterThanOrEqual(0);
    expect(callLine).toBeGreaterThan(startLine);
    expect(SOURCE).toMatch(/_maybeRender\s*\(\s*[^,]+,\s*[^,]+,\s*sources\s*\)/);
  });
});
