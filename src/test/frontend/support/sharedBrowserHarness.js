import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SIDEBAR = resolve(__dirname, '../../../main/resources/templates/fragments/layout/scripts-sidebar.html');
const APP_JS = resolve(__dirname, '../../../main/resources/static/js/app.js');

function slBrowseFilesSource() {
  const text = readFileSync(APP_JS, 'utf8');
  const start = text.indexOf('window.SLBrowseFiles = async function');
  const end = text.indexOf('\n};', start);
  if (start < 0 || end < 0) throw new Error('SLBrowseFiles not found in app.js');
  return text.slice(start, end + 3);
}

export function installSLBrowseFiles(targetWindow) {
  targetWindow.eval(slBrowseFilesSource());
}

export function sharedBrowserSource() {
  const file = readFileSync(SIDEBAR, 'utf8');
  const start = file.indexOf('function __kiChatLoadDirectoryEntries');
  const end = file.indexOf('</script>', start);
  if (start < 0 || end < 0) throw new Error('shared dir browser not found in scripts-sidebar.html');
  return file.slice(start, end);
}

export function bootSharedBrowser(responses = []) {
  const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', { runScripts: 'dangerously' });
  const win = dom.window;
  win.__kiChatEsc = (s) => String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  win.i18n = { t: (key, fallback) => fallback || key };
  const calls = [];
  win.fetch = (url) => {
    calls.push(url);
    const match = responses.find((r) => url.includes(r.match)) || responses[0];
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(match.body) });
  };
  const script = win.document.createElement('script');
  script.textContent = sharedBrowserSource();
  win.document.body.appendChild(script);
  return { win, dom, calls };
}

export function flush() {
  return new Promise((r) => setTimeout(r, 0));
}

export function browserRows(win) {
  return [...win.document.querySelectorAll('#kiDbList button')];
}

export function rowByText(win, text) {
  return browserRows(win).find((r) => r.textContent.includes(text));
}

export function staticSource(path) {
  return readFileSync(resolve(__dirname, '../../../main/resources/templates', path), 'utf8');
}

export function staticJsSource(path) {
  return readFileSync(resolve(__dirname, '../../../main/resources/static', path), 'utf8');
}

export function fragmentSource(path) {
  return readFileSync(resolve(__dirname, '../../../main/resources/templates/fragments', path), 'utf8');
}
