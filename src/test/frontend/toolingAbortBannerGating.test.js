import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FOCUS = resolve(__dirname, '../../main/resources/templates/fragments/tooling/scripts-focus-view.html');
const TOOL_VIEW = resolve(__dirname, '../../main/resources/templates/tool-view.html');

const ABORT_MESSAGE = 'Tool-Generierung wurde abgebrochen. Starte die Generierung neu.';

function tool(overrides) {
  return Object.assign(
    {
      id: 17,
      name: 'Tetris',
      status: 'READY',
      confluencePageUrl: 'SCRIPT',
      createdAt: '2026-09-06T17:49:00',
      lastRunAt: '2026-09-06T17:54:00',
      generatedCode: 'fun main() {}',
      generationPrompt: null,
      errorMessage: null,
    },
    overrides,
  );
}

function renderedFocusCard(t) {
  const win = makeWindow('<div id="focusCard"></div>');
  runInWindow(win, [
    'window.escHtml = function (s) { return String(s == null ? "" : s); };',
    'window.i18n = null;',
    extractFunction(readFileSync(FOCUS, 'utf8'), 'renderFocusCard'),
    'renderFocusCard(' + JSON.stringify(t) + ');',
  ].join('\n;\n'));
  return win.document.getElementById('focusCard').innerHTML;
}

describe('Tool-View: Abbruch-Banner nur bei echtem Fehlschlag', () => {
  it('zeigt kein Banner, wenn ein fertiges Tool eine alte Abbruchmeldung mitschleppt', () => {
    const html = renderedFocusCard(tool({ status: 'READY', errorMessage: ABORT_MESSAGE }));
    expect(html).not.toContain('abgebrochen');
    expect(html).not.toContain('text-danger');
  });

  it('zeigt Banner und Neustart-Aktion, wenn die Generierung abgebrochen ist', () => {
    const html = renderedFocusCard(tool({ status: 'CANCELLED', errorMessage: ABORT_MESSAGE }));
    expect(html).toContain('abgebrochen');
    expect(html).toContain('editTool(17)');
  });

  it('zeigt Banner und Neustart-Aktion auch bei FAILED', () => {
    const html = renderedFocusCard(tool({ status: 'FAILED', errorMessage: 'compile failed' }));
    expect(html).toContain('compile failed');
    expect(html).toContain('editTool(17)');
  });

  it('versteckt im Detail-Dialog dasselbe Banner bei READY', () => {
    const source = readFileSync(TOOL_VIEW, 'utf8');
    expect(source).toContain("t.status === 'CANCELLED'");
    expect(source).toMatch(/id="tvErrorMsg"[\s\S]{0,160}tool\.status == 'FAILED' or tool\.status == 'CANCELLED'/);
  });
});
