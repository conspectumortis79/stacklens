import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, makeWindow, readJs, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..', '..', '..');
const IFRAME_DOCUMENT = resolve(ROOT, 'src/main/resources/static/js/iframe-document.js');
const ANALYSIS_UI = resolve(ROOT, 'src/main/resources/templates/fragments/review-analysis/scripts-analysis-ui.html');
const KI_CHAT_WINDOW = resolve(ROOT, 'src/main/resources/templates/fragments/layout/scripts-ki-chat-window.html');
const LAYOUT = resolve(ROOT, 'src/main/resources/templates/layout.html');

function installObjectUrlApi(win) {
  const created = [];
  const revoked = [];
  let counter = 0;
  win.URL.createObjectURL = (blob) => {
    const url = 'blob:http://localhost/frame-' + (++counter);
    created.push({ url, text: blob });
    return url;
  };
  win.URL.revokeObjectURL = (url) => { revoked.push(url); };
  return { created, revoked };
}

function bootFrameDocumentApi(withObjectUrls) {
  const win = makeWindow();
  const api = withObjectUrls ? installObjectUrlApi(win) : null;
  runInWindow(win, readJs(IFRAME_DOCUMENT));
  return { win, api };
}

describe('iframe document loading via object URL', () => {
  it('loads markup through a blob URL and clears any srcdoc attribute', () => {
    const { win, api } = bootFrameDocumentApi(true);
    const iframe = win.document.createElement('iframe');
    iframe.setAttribute('srcdoc', '<html><body>stale</body></html>');

    win.stacklensSetFrameHtml(iframe, '<html><head><style>i{x:1}</style></head><body>ok</body></html>');

    expect(iframe.getAttribute('srcdoc')).toBeNull();
    expect(iframe.src).toBe('blob:http://localhost/frame-1');
    expect(api.created).toHaveLength(1);
    expect(api.created[0].text.type).toBe('text/html');
  });

  it('releases the previous object url when new markup arrives', () => {
    const { win, api } = bootFrameDocumentApi(true);
    const iframe = win.document.createElement('iframe');

    win.stacklensSetFrameHtml(iframe, '<html><body>one</body></html>');
    win.stacklensSetFrameHtml(iframe, '<html><body>two</body></html>');

    expect(api.revoked).toEqual(['blob:http://localhost/frame-1']);
    expect(iframe.src).toBe('blob:http://localhost/frame-2');
  });

  it('releases the object url once the frame loaded, but only once', () => {
    const { win, api } = bootFrameDocumentApi(true);
    const iframe = win.document.createElement('iframe');

    win.stacklensSetFrameHtml(iframe, '<html><body>one</body></html>');
    iframe.dispatchEvent(new win.Event('load'));
    iframe.dispatchEvent(new win.Event('load'));

    expect(api.revoked).toEqual(['blob:http://localhost/frame-1']);
  });

  it('falls back to srcdoc while no object url api exists', () => {
    const { win } = bootFrameDocumentApi(false);
    const iframe = win.document.createElement('iframe');

    win.stacklensSetFrameHtml(iframe, '<html><body>fallback</body></html>');

    expect(iframe.srcdoc).toBe('<html><body>fallback</body></html>');
  });

  it('falls back to srcdoc when the browser removed the object url api again', () => {
    const { win } = bootFrameDocumentApi(true);
    delete win.URL.createObjectURL;
    const iframe = win.document.createElement('iframe');

    win.stacklensSetFrameHtml(iframe, '<html><body>fallback</body></html>');

    expect(iframe.srcdoc).toBe('<html><body>fallback</body></html>');
  });

  it('treats missing markup as an empty document and ignores missing frames', () => {
    const { win, api } = bootFrameDocumentApi(true);
    const iframe = win.document.createElement('iframe');

    expect(() => win.stacklensSetFrameHtml(null, '<html></html>')).not.toThrow();
    win.stacklensSetFrameHtml(iframe, null);

    expect(api.created).toHaveLength(1);
    expect(iframe.src).toBe('blob:http://localhost/frame-1');
  });
});

describe('inlined chart.js carries no source map reference', () => {
  function bootAnalysisUi() {
    const win = makeWindow();
    runInWindow(win, extractInlineScripts(ANALYSIS_UI));
    return win;
  }

  it('drops a trailing line comment source map reference', () => {
    const win = bootAnalysisUi();
    expect(win.raStripSourceMapComment('var chart=1;\n//# sourceMappingURL=chart.umd.js.map\n')).toBe('var chart=1;');
  });

  it('drops a trailing block comment source map reference', () => {
    const win = bootAnalysisUi();
    expect(win.raStripSourceMapComment('a{color:red}\n/*# sourceMappingURL=bootstrap.min.css.map */\n')).toBe('a{color:red}');
  });

  it('keeps code without a trailing source map reference untouched', () => {
    const win = bootAnalysisUi();
    expect(win.raStripSourceMapComment('var a=1; // note\n')).toBe('var a=1; // note\n');
    expect(win.raStripSourceMapComment('//# sourceMappingURL=a.map\nvar b=2;\n')).toBe('//# sourceMappingURL=a.map\nvar b=2;\n');
    expect(win.raStripSourceMapComment('')).toBe('');
    expect(win.raStripSourceMapComment(null)).toBeNull();
  });

  it('inlines the vendor chart without any sourceMappingURL marker', () => {
    const win = bootAnalysisUi();
    const html = '<html><head></head><body><script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script></body></html>';

    const patched = win.raInlineChartJs(html, 'window.Chart=function(){};\n//# sourceMappingURL=chart.umd.js.map\n');

    expect(patched).toContain('id="stacklens-chartjs-inline"');
    expect(patched).not.toContain('sourceMappingURL');
  });

  it('the shipped vendor chart carries no source map reference any more', () => {
    const vendor = readFileSync(resolve(ROOT, 'src/main/resources/static/js/grafana/chart.umd.min.js'), 'utf8');
    const win = bootAnalysisUi();

    expect(vendor).not.toMatch(/sourceMappingURL/);
    expect(vendor).toContain('window.Chart');
    expect(win.raStripSourceMapComment(vendor)).toBe(vendor);
  });

  it('inlining still strips a reference when a future vendor build adds one', () => {
    const win = bootAnalysisUi();
    const vendorWithMap = 'window.Chart=function(){};\n//# sourceMappingURL=chart.umd.js.map\n';

    expect(win.raStripSourceMapComment(vendorWithMap)).toBe('window.Chart=function(){};');
  });
});

describe('generated documents never rely on about:srcdoc', () => {
  const frameFiles = [
    ['review analysis dashboard', ANALYSIS_UI],
    ['ki chat window', KI_CHAT_WINDOW],
  ];

  it.each(frameFiles)('%s renders frames through the shared frame document api', (_label, file) => {
    const code = readFileSync(file, 'utf8');
    expect(code).not.toMatch(/\.srcdoc\s*=/);
    expect(code).toContain('window.stacklensSetFrameHtml(');
  });

  it('the shell provides the frame document api to every page', () => {
    expect(readFileSync(LAYOUT, 'utf8')).toContain('<script th:src="@{/js/iframe-document.js}"></script>');
  });
});
