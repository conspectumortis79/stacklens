import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  flushMicrotasks, untrackDocumentListeners
} from './_load.js';

installBrowserStubs();

const TEMPLATES = resolve(process.cwd(), 'src/main/resources/templates');
const APP_JS = resolve(process.cwd(), 'src/main/resources/static/js/app.js');

afterEach(() => {
  untrackDocumentListeners();
  delete window.__spaOrder;
  delete window.__fakeGlobal;
  delete window.__probe;
  delete window.SpaPageScripts;
  delete window.navigateTo;
});

describe('spa-page-scripts.js — collect', () => {
  beforeEach(() => {
    resetDom();
    loadScriptIsolated(window, 'js/spa-page-scripts.js');
  });

  it('erfasst externe Skripte mit src und data-page-script-Markierung in Dokumentreihenfolge', () => {
    const tags = window.SpaPageScripts.collect(
      '<script src="/js/layout-core.js"></script>'
      + '<script src="/js/fragments/page.js" data-page-script="1"></script>'
      + '<script>window.a = 1;</script>'
    );
    expect(tags.map((t) => t.src)).toEqual(['/js/layout-core.js', '/js/fragments/page.js', null]);
    expect(tags[0].pageScript).toBe(false);
    expect(tags[1].pageScript).toBe(true);
    expect(tags[2].code).toContain('window.a = 1;');
  });

  it('überspringt leere Inline-Skripte und src-lose Platzhalter', () => {
    const tags = window.SpaPageScripts.collect('<script>   </script><script></script>');
    expect(tags).toEqual([]);
  });

  it('liefert für leeren HTML-Text eine leere Liste', () => {
    expect(window.SpaPageScripts.collect('')).toEqual([]);
  });
});

describe('spa-page-scripts.js — runAfterMain', () => {
  beforeEach(() => {
    resetDom();
    loadScriptIsolated(window, 'js/spa-page-scripts.js');
    window.__spaOrder = [];
  });

  it('lädt nur markierte externe Skripte und führt Inline-Skripte in Dokumentreihenfolge aus', async () => {
    const deps = {
      loadScript: (src) => { window.__spaOrder.push('load:' + src); return Promise.resolve(); },
      runScript: (code) => { window.__spaOrder.push('run:' + code.trim()); return undefined; }
    };
    await window.SpaPageScripts.runAfterMain(
      '<script src="/js/layout-core.js"></script>'
      + '<script>window.first = 1;</script>'
      + '<script src="/js/fragments/page.js" data-page-script="1"></script>'
      + '<script>window.second = 2;</script>',
      deps
    );
    expect(window.__spaOrder).toEqual([
      'run:window.first = 1;',
      'load:/js/fragments/page.js',
      'run:window.second = 2;'
    ]);
  });

  it('wartet das externe Seitenskript ab, bevor nachfolgende Inline-Skripte laufen', async () => {
    let release;
    const deps = {
      loadScript: () => new Promise((r) => { release = () => { window.__spaOrder.push('loaded'); r(); }; }),
      runScript: () => { window.__spaOrder.push('inline'); }
    };
    const p = window.SpaPageScripts.runAfterMain(
      '<script src="/js/p.js" data-page-script="1"></script><script>x();</script>',
      deps
    );
    await flushMicrotasks();
    expect(window.__spaOrder).toEqual([]);
    release();
    await p;
    expect(window.__spaOrder).toEqual(['loaded', 'inline']);
  });

  it('bricht ab, sobald isCancelled wahr liefert', async () => {
    const deps = {
      loadScript: () => { window.__spaOrder.push('load'); return Promise.resolve(); },
      runScript: () => { window.__spaOrder.push('run'); },
      isCancelled: () => true
    };
    await window.SpaPageScripts.runAfterMain('<script>a();</script>', deps);
    expect(window.__spaOrder).toEqual([]);
  });
});

describe('app.js — SPA-Navigation lädt markierte Seitenskripte vor den Inline-Skripten', () => {
  beforeEach(() => {
    resetDom();
    loadScriptIsolated(window, 'js/spa-page-scripts.js');
    loadScriptIsolated(window, 'js/app.js');
    window.__spaOrder = [];
    window.SpaPageScripts.loadScript = (src) => {
      window.__spaOrder.push('load:' + src);
      window.__fakeGlobal = 'definiert';
      return Promise.resolve();
    };
    document.body.innerHTML = '<main><p>alt</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/alt');
  });
  it('stellt die Globals des externen Seitenskripts bereit, bevor Inline-Skripte ausgeführt werden', async () => {
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve(
        '<html><head><title>Neu</title></head><body><main><p>neu</p></main>'
        + '<script src="/js/fragments/fake.js" data-page-script="1"></script>'
        + '<script>window.useFakeGlobal();</script>'
        + '</body></html>'
      )
    });
    window.SpaPageScripts.runScript = (code) => {
      window.__spaOrder.push('run:' + (window.__fakeGlobal || 'fehlend'));
    };
    await window.navigateTo('/neu');
    await flushMicrotasks();
    expect(window.__spaOrder).toEqual(['load:/js/fragments/fake.js', 'run:definiert']);
  });

  it('lädt nicht markierte Body-Skripte (Layout-Kern) bei der SPA-Navigation nicht erneut', async () => {
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve(
        '<html><head><title>Neu</title></head><body><main><p>neu</p></main>'
        + '<script src="/js/app-abc123.js"></script>'
        + '<script src="/js/marked.min-def456.js"></script>'
        + '</body></html>'
      )
    });
    await window.navigateTo('/neu');
    await flushMicrotasks();
    expect(window.__spaOrder).toEqual([]);
  });
});

describe('SPA-Seitenskript-Vertrag in den Templates', () => {
  const expectedMarked = [
    'fragments/pipeline-analysis/scripts.html:/js/fragments/pipeline-analysis-mermaid.js',
    'fragments/pipeline-analysis/scripts.html:/js/fragments/pipeline-analysis.js',
    'fragments/code-qa/scripts.html:/js/mr-violations.js',
    'fragments/code-qa/scripts.html:/js/mr-link-builder.js',
    'fragments/code-qa/scripts.html:/js/fragments/code-qa-it-panel.js',
    'fragments/code-qa/scripts.html:/js/fragments/code-qa.js',
    'fragments/search/scripts.html:/js/fragments/search-render.js',
    'fragments/search/scripts.html:/js/fragments/search.js',
    'fragments/ticket-creation/scripts.html:/js/fragments/ticket-creation-render.js',
    'fragments/ticket-creation/scripts.html:/js/fragments/ticket-creation.js',
    'fragments/ticket-refinement/scripts.html:/js/fragments/ticket-refinement-markdown.js',
    'fragments/ticket-refinement/scripts.html:/js/fragments/ticket-refinement.js'
  ];

  it('markiert jedes externe Seitenskript nach </main> mit data-page-script', () => {
    for (const entry of expectedMarked) {
      const [rel, src] = entry.split(':');
      const html = readFileSync(resolve(TEMPLATES, rel), 'utf8');
      expect(
        html,
        rel + ' muss <script th:src="@{' + src + '}" data-page-script="1"> enthalten'
      ).toContain('<script th:src="@{' + src + '}" data-page-script="1"></script>');
    }
  });

  it('logging.html markiert logging-analysis.js als Seitenskript', () => {
    const html = readFileSync(resolve(TEMPLATES, 'logging.html'), 'utf8');
    expect(html).toContain('<script th:src="@{/js/logging-analysis.js}" data-page-script="1"></script>');
  });

  it('layout.html lädt spa-page-scripts.js vor app.js', () => {
    const html = readFileSync(resolve(TEMPLATES, 'layout.html'), 'utf8');
    const spaIdx = html.indexOf('spa-page-scripts.js');
    const appIdx = html.indexOf('@{/js/app.js}');
    expect(spaIdx).toBeGreaterThan(-1);
    expect(appIdx).toBeGreaterThan(-1);
    expect(spaIdx).toBeLessThan(appIdx);
  });

  it('app.js delegiert die Body-Skripte an SpaPageScripts.runAfterMain', () => {
    const code = readFileSync(APP_JS, 'utf8');
    expect(code).toContain('await window.SpaPageScripts.runAfterMain(afterMain');
    expect(code).not.toMatch(/bsRe\s*=\s*\/<script\\b/);
  });
});
