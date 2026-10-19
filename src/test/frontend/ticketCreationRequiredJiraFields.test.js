import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJs, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PAGE = resolve(__dirname, '../../main/resources/templates/ticket-creation.html');
const SCRIPT = resolve(__dirname, '../../main/resources/static/js/fragments/ticket-creation.js');
const RENDER_SCRIPT = resolve(__dirname, '../../main/resources/static/js/fragments/ticket-creation-render.js');
const APP = resolve(__dirname, '../../main/resources/static/js/app.js');

const REQUIRED_FIELDS = [
  { fieldId: 'customfield_10108', name: 'Bug Environment', multiple: true, allowedValues: ['Live', 'Staging'] },
  { fieldId: 'customfield_10120', name: 'Department', multiple: false, allowedValues: ['Shop Platform', 'Tax'] },
];

function pageContentHtml() {
  const html = readFileSync(PAGE, 'utf8');
  const start = html.indexOf('<div layout:fragment="content">');
  const end = html.indexOf('<th:block th:replace="~{fragments/shared/history');
  return html.slice(start, end).replace(/<script\b[\s\S]*?<\/script>/gi, '');
}

function json(body, status = 200) {
  return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) });
}

function boot(requiredFields) {
  const dom = new JSDOM('<!DOCTYPE html><html><body>' + pageContentHtml() + '</body></html>', {
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    url: 'http://localhost/',
  });
  const win = dom.window;
  win.i18n = { onReady: (cb) => cb(), t: (key) => key };
  win.__requests = [];
  win.fetch = vi.fn((url, opts) => {
    const u = String(url);
    win.__requests.push({ url: u, body: opts && opts.body ? JSON.parse(opts.body) : null });
    if (u === '/api/config/jira') return json([{ id: 1, name: 'jira-test' }]);
    if (u === '/api/ticket-creation/generate') {
      return json({ summary: 'Export bricht ab', description: '## 🐛 Bug Description\nX', issueType: 'Bug', historyId: 5 });
    }
    if (u.startsWith('/api/ticket-creation/required-fields')) return json(requiredFields);
    if (u === '/api/ticket-creation/create-jira') return json({ key: 'PROJ-1', url: 'https://jira/browse/PROJ-1', subtasks: [] });
    return json([]);
  });
  runInWindow(win, readJs(APP));
  runInWindow(win, readJs(RENDER_SCRIPT));
  runInWindow(win, readJs(SCRIPT));
  return win;
}

const settle = (ms = 450) => new Promise((r) => setTimeout(r, ms));

async function generateBug(win) {
  const doc = win.document;
  await settle(20);
  doc.getElementById('tcProjectKey').value = 'PROJ';
  doc.getElementById('tcInputText').value = 'CSV-Export bricht ab';
  doc.getElementById('tcStart').click();
  await settle();
}

describe('Ticket-Erstellung: Jira-Pflichtfelder', () => {
  let win;
  afterEach(() => { try { win.close(); } catch (_) {} });

  describe('wenn Jira Pflichtfelder verlangt', () => {
    beforeEach(async () => {
      win = boot(REQUIRED_FIELDS);
      await generateBug(win);
    });

    it('lädt die Pflichtfelder für Projekt, Konfiguration und Vorgangstyp', () => {
      const req = win.__requests.find((r) => r.url.startsWith('/api/ticket-creation/required-fields'));
      expect(req.url).toBe('/api/ticket-creation/required-fields?jiraConfigId=1&projectKey=PROJ&issueType=Bug');
    });

    it('zeigt Mehrfachauswahl als Checkboxen und Einfachauswahl als Dropdown', () => {
      const doc = win.document;
      const boxes = doc.querySelectorAll('#tcRequiredFields input[type=checkbox][data-field-id="customfield_10108"]');
      expect(Array.from(boxes).map((b) => b.value)).toEqual(['Live', 'Staging']);
      const sel = doc.querySelector('#tcRequiredFields select[data-field-id="customfield_10120"]');
      expect(Array.from(sel.options).map((o) => o.value)).toEqual(['', 'Shop Platform', 'Tax']);
    });

    it('blockiert das Erstellen, solange Pflichtfelder leer sind', async () => {
      win.document.getElementById('tcBtnCreate').click();
      await settle(20);

      expect(win.__requests.some((r) => r.url === '/api/ticket-creation/create-jira')).toBe(false);
      const status = win.document.getElementById('tcCreateStatus').textContent;
      expect(status).toContain('Bug Environment');
      expect(status).toContain('Department');
    });

    it('schickt die gewählten Werte als customFields mit', async () => {
      const doc = win.document;
      doc.querySelector('#tcRequiredFields input[value="Live"]').checked = true;
      doc.querySelector('#tcRequiredFields select[data-field-id="customfield_10120"]').value = 'Tax';
      doc.getElementById('tcBtnCreate').click();
      await settle(20);

      const create = win.__requests.find((r) => r.url === '/api/ticket-creation/create-jira');
      expect(create.body.customFields).toEqual({ customfield_10108: ['Live'], customfield_10120: 'Tax' });
      expect(create.body.issueType).toBe('Bug');
    });
  });

  it('erstellt ohne Zusatzfelder, wenn Jira keine verlangt', async () => {
    win = boot([]);
    await generateBug(win);

    expect(win.document.getElementById('tcRequiredFields').children.length).toBe(0);
    win.document.getElementById('tcBtnCreate').click();
    await settle(20);

    const create = win.__requests.find((r) => r.url === '/api/ticket-creation/create-jira');
    expect(create.body.customFields).toEqual({});
  });
});
