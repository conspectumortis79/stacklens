import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const __dirname = dirname(fileURLToPath(import.meta.url));
const RESOURCES = resolve(__dirname, '../../../main/resources');
const SIDEBAR = resolve(RESOURCES, 'templates/fragments/layout/scripts-sidebar.html');
const CONFIG_IO = resolve(RESOURCES, 'templates/fragments/config/scripts-io-browser.html');
const APP_JS = resolve(RESOURCES, 'static/js/app.js');

export function sliceSource(file, from, to, includeEnd = false) {
  const text = readFileSync(file, 'utf8');
  const start = text.indexOf(from);
  const end = text.indexOf(to, start);
  if (start < 0 || end < 0) throw new Error(`marker missing in ${file}: ${from}`);
  return includeEnd ? text.slice(start, end + to.length) : text.slice(start, end);
}

export const HOME_DIR = {
  currentPath: '/Users/me',
  parentPath: '/',
  items: [
    { name: 'configs', path: '/Users/me/configs', type: 'directory' },
  ],
};

export const CONFIG_DIR = {
  currentPath: '/Users/me/configs',
  parentPath: '/Users/me',
  items: [
    { name: 'stacklens-config.json', path: '/Users/me/configs/stacklens-config.json', type: 'file' },
  ],
};

export const DEFAULT_BROWSE_BODIES = {
  '/Users/me': HOME_DIR,
  '/Users/me/configs': CONFIG_DIR,
};

function jsonResponse(body) {
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
}

const MESSAGES = {
  'config.export_saved': 'Konfiguration gespeichert: ',
  'common.network_error': 'Netzwerkfehler: ',
  'config.filename_empty': 'Dateiname darf nicht leer sein.',
  'common.save_here': 'Hier speichern',
  'common.cancel': 'Abbrechen',
  'common.dir_browse_title': 'Verzeichnis auswählen',
  'dirbrowser.save_btn': 'Hier speichern',
  'dirbrowser.pick_btn': 'Diesen Ordner wählen',
  'dirbrowser.file_hint': 'Datei antippen zum Auswählen',
  'dirbrowser.selection_label': 'Auswahl:',
  'dirbrowser.empty_hint': '— keine Unterverzeichnisse —',
  'dirbrowser.empty_files_hint': '— keine passenden Dateien in diesem Ordner —',
};

export function bootConfigIo(options = {}) {
  const dom = new JSDOM(
    '<!DOCTYPE html><html data-theme="modern"><body><div id="alertArea"></div></body></html>',
    { runScripts: 'dangerously', url: 'http://localhost/' },
  );
  const win = dom.window;
  const calls = [];
  const alerts = [];
  const saveRequests = [];
  const browseBodies = options.browseBodies || DEFAULT_BROWSE_BODIES;
  win.__kiChatEsc = (value) => String(value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  win.i18n = { t: (key, fallback) => MESSAGES[key] || fallback || key };
  win._t = (key) => win.i18n.t(key);
  win.showAlert = (message, kind) => { alerts.push([message, kind]); };
  win.fetch = (url, init) => {
    calls.push(String(url));
    if (String(url).includes('/api/config/save-to-path')) {
      saveRequests.push(JSON.parse(init.body));
      if (options.saveFailure) return Promise.reject(new Error(options.saveFailure));
      return jsonResponse(options.saveBody || { savedPath: '/Users/me/configs/stacklens-config.json' });
    }
    if (String(url).includes('/api/prompts/read-file')) {
      if (typeof options.readFile === 'function') return options.readFile(String(url));
      return jsonResponse(options.readFileBody || { content: '{"gitlab":[1]}' });
    }
    const query = new URLSearchParams(String(url).split('?')[1] || '');
    return jsonResponse(browseBodies[query.get('path') || '/Users/me'] || HOME_DIR);
  };
  const script = win.document.createElement('script');
  script.textContent = [
    sliceSource(APP_JS, 'window.SLBrowseFiles = async function', '\n};', true),
    sliceSource(SIDEBAR, 'function __kiChatLoadDirectoryEntries', '</script>'),
    sliceSource(CONFIG_IO, 'var CF_IMPORT_EXTS', '</script>'),
  ].join('\n;\n');
  win.document.body.appendChild(script);
  return { win, dom, calls, alerts, saveRequests };
}

export function flush() {
  return new Promise((resolveNext) => setTimeout(resolveNext, 0));
}

export function readResponse(body) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}

export function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolveNext, rejectNext) => {
    resolve = resolveNext;
    reject = rejectNext;
  });
  return { promise, resolve, reject };
}

export function browserModal(win) {
  return win.document.getElementById('kiChatDirBrowserModal');
}

export function browserRows(win) {
  return [...win.document.querySelectorAll('#kiDbList button')];
}

export function rowByText(win, text) {
  return browserRows(win).find((row) => row.textContent.includes(text));
}

export function rowLabels(win) {
  return browserRows(win).map((row) => row.textContent.trim());
}

export function elementById(win, id) {
  return win.document.getElementById(id);
}
