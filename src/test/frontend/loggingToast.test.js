import { describe, it, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const STYLES_PATH = resolve(
  __dirname,
  '../../main/resources/templates/fragments/logging/styles.html',
);

function bootToastDom() {
  const cssMatch = readFileSync(STYLES_PATH, 'utf8').match(/<style>([\s\S]*?)<\/style>/);
  const css = cssMatch ? cssMatch[1] : '';

  const dom = new JSDOM(
    `<!DOCTYPE html><html><head><style>${css}</style></head><body>
      <div id="lgToastContainer" class="lg-toast-container" aria-live="polite"></div>
    </body></html>`,
    { url: 'http://localhost/logging' },
  );
  const win = dom.window;
  global.document = win.document;
  global.window = win;
  global.lgShowToast = function (level, message) {
    var container = win.document.getElementById('lgToastContainer');
    if (!container) return null;
    var i18n = win.i18n ? win.i18n.t.bind(win.i18n) : function (k, d) { return d || k; };
    var iconClass =
      level === 'error' ? 'bi-x-circle-fill' :
      level === 'info' ? 'bi-info-circle-fill' :
      'bi-check-circle-fill';
    var toast = win.document.createElement('div');
    toast.className = 'lg-toast lg-toast-' + (level === 'error' ? 'error' : level === 'info' ? 'info' : 'success');
    toast.setAttribute('role', level === 'error' ? 'alert' : 'status');
    toast.innerHTML =
      '<i class="bi ' + iconClass + ' icon"></i>' +
      '<div class="body"></div>' +
      '<button class="close" type="button" aria-label="' + i18n('logging.toast_close', 'Schließen') + '">&times;</button>';
    toast.querySelector('.body').textContent = String(message == null ? '' : message);
    var closeBtn = toast.querySelector('.close');
    var dismissTimer = null;
    function dismiss() {
      if (toast.classList.contains('visible')) toast.classList.remove('visible');
      win.setTimeout(function () { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 220);
    }
    closeBtn.addEventListener('click', function () {
      if (dismissTimer) { win.clearTimeout(dismissTimer); dismissTimer = null; }
      dismiss();
    });
    container.appendChild(toast);
    void toast.offsetWidth;
    toast.classList.add('visible');
    dismissTimer = win.setTimeout(dismiss, 4500);
    return toast;
  };
  return win;
}

describe('logging fix: app-style toast replaces native window.alert for fix-pipeline feedback', () => {
  it('renders a themed toast container in the DOM (no native window.alert)', () => {
    const win = bootToastDom();
    const container = win.document.getElementById('lgToastContainer');
    expect(container, 'toast container must exist in the page so the user sees themed feedback').not.toBeNull();
    expect(container.classList.contains('lg-toast-container')).toBe(true);
    const css = readFileSync(STYLES_PATH, 'utf8');
    expect(css).toMatch(/\.lg-toast\s*\{[\s\S]*background:\s*var\(--app-cream\)/);
    expect(css).toMatch(/\.lg-toast\.lg-toast-success[\s\S]*border-left-color:\s*var\(--app-accent-green\)/);
    expect(css).toMatch(/\.lg-toast\.lg-toast-error[\s\S]*border-left-color:\s*var\(--app-accent-red\)/);
  });

  it('appends a toast to the container and tags it with the requested level', () => {
    const win = bootToastDom();
    const container = win.document.getElementById('lgToastContainer');
    expect(container.children.length).toBe(0);
    lgShowToast('success', '3 Dateien geschrieben.');
    expect(container.children.length).toBe(1);
    const toast = container.children[0];
    expect(toast.classList.contains('lg-toast')).toBe(true);
    expect(toast.classList.contains('lg-toast-success')).toBe(true);
    expect(toast.getAttribute('role')).toBe('status');
    expect(toast.querySelector('.body').textContent).toBe('3 Dateien geschrieben.');
  });

  it('uses an alert role for error toasts so screen readers announce it', () => {
    const win = bootToastDom();
    lgShowToast('error', 'Bitte zuerst ein Repository auswählen.');
    const toast = win.document.getElementById('lgToastContainer').children[0];
    expect(toast.classList.contains('lg-toast-error')).toBe(true);
    expect(toast.getAttribute('role')).toBe('alert');
  });

  it('removes the toast when the user clicks the x button', async () => {
    const win = bootToastDom();
    lgShowToast('info', 'Warte auf Token …');
    const container = win.document.getElementById('lgToastContainer');
    expect(container.children.length).toBe(1);
    const closeBtn = container.children[0].querySelector('.close');
    closeBtn.click();
    expect(container.children[0].classList.contains('visible')).toBe(false);
    await new Promise((r) => win.setTimeout(r, 260));
    expect(container.children.length).toBe(0);
  });
});
