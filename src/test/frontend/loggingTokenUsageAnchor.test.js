import { describe, it, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const LOGGING_PATH = resolve(
  __dirname,
  '../../main/resources/templates/logging.html',
);

function loadLoggingDom() {
  const html = readFileSync(LOGGING_PATH, 'utf8')
    .replace(/th:[a-z]+(-[a-z]+)*="[^"]*"/g, '')
    .replace(/layout:[a-z]+(-[a-z]+)*="[^"]*"/g, '')
    .replace(/data-i18n(-[a-z]+)*="[^"]*"/g, '')
    .replace(/data-i18n(-[a-z]+)*-[a-z]+="[^"]*"/g, '')
    .replace(/<th:block[^>]*\/>/g, '')
    .replace(/<!\[CDATA\[/g, '')
    .replace(/\]\]>/g, '');

  return new JSDOM(html, {
    pretendToBeVisual: true,
    url: 'http://localhost/logging',
  });
}

describe('logging: Token-Usage-Badge muss ÜBER dem Log-Eingabe-Fenster sitzen', () => {
  it('deklariert data-cf-token-usage-anchor auf dem äußeren .lg-page-Wrapper, NICHT auf der <h2> im Log-Eingabe-Fenster', () => {
    const dom = loadLoggingDom();
    const { document } = dom.window;
    const allAnchors = document.querySelectorAll('[data-cf-token-usage-anchor]');
    expect(allAnchors.length).toBe(1);
    const anchor = allAnchors[0];
    expect(anchor.classList.contains('lg-page')).toBe(true);
    expect(anchor.closest('.lg-card')).toBeNull();
    const innerH2 = Array.from(document.querySelectorAll('.lg-card h2'))
      .find((el) => (el.textContent || '').includes('Log-Eingabe'));
    expect(innerH2).toBeDefined();
    expect(innerH2.hasAttribute('data-cf-token-usage-anchor')).toBe(false);
  });

  it('plaziert den tug-bar AUSSERHALB der Log-Eingabe-Card, direkt nach dem .sl-page-header', () => {
    const dom = new JSDOM(
      `<body>
        <div class="sl-page-header">Log-Analyse</div>
        <div class="lg-page" data-cf-token-usage-anchor="">
          <div class="lg-card">
            <h2>Log-Eingabe</h2>
            <div class="mb-3">
              <div class="lg-label">Log-Text</div>
              <textarea id="lgInput"></textarea>
            </div>
            <div>
              <button id="lgBtnAnalyze">Analysieren</button>
            </div>
          </div>
        </div>
      </body>`,
      { url: 'http://localhost/logging' },
    );
    const { document } = dom.window;
    const pageHeader = document.querySelector('.sl-page-header');
    const lgPage = document.querySelector('.lg-page');
    const card = document.querySelector('.lg-card');
    const anchor = pageHeader;
    const bar = document.createElement('div');
    bar.id = 'tug-bar';
    bar.className = 'tug-bar';

    anchor.parentNode.insertBefore(bar, anchor.nextSibling);

    expect(card.children.length).toBe(3);
    expect(card.children[0].tagName.toLowerCase()).toBe('h2');
    expect(card.children[1].className).toBe('mb-3');
    expect(card.children[2].tagName.toLowerCase()).toBe('div');

    const badge = document.getElementById('tug-bar');
    expect(badge.parentElement.classList.contains('lg-card')).toBe(false);
    expect(badge.parentElement).toBe(document.body);
    expect(badge.previousElementSibling).toBe(pageHeader);
    expect(badge.nextElementSibling).toBe(lgPage);

    const ta = document.getElementById('lgInput');
    expect(
      (bar.compareDocumentPosition(ta) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0,
    ).toBe(true);
  });

  it('init-Skript verwendet .sl-page-header als Anker (gleiches Muster wie code-qa / pipeline-analysis)', () => {
    const src = readFileSync(LOGGING_PATH, 'utf8');
    expect(src).toContain("anchor: document.querySelector('.sl-page-header')");
    expect(src).not.toContain(
      "anchor: document.querySelector('[data-cf-token-usage-anchor]')",
    );
  });
});
