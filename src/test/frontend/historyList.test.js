import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  installBrowserStubs, resetDom, readSource, loadScript,
} from './_load.js';

installBrowserStubs();

function ensureLocalStorage() {
  if (typeof window.localStorage !== 'undefined' && window.localStorage && typeof window.localStorage.getItem === 'function') {
    return window.localStorage;
  }
  const store = new Map();
  const stub = {
    getItem: function(k) { return store.has(k) ? store.get(k) : null; },
    setItem: function(k, v) { store.set(String(k), String(v)); },
    removeItem: function(k) { store.delete(k); },
    clear: function() { store.clear(); },
    key: function(i) { return Array.from(store.keys())[i] || null; },
    get length() { return store.size; }
  };
  try {
    Object.defineProperty(window, 'localStorage', { value: stub, configurable: true });
  } catch (_) {
    window.localStorage = stub;
  }
  return stub;
}
ensureLocalStorage();

const ITEMS = [
  { id: 1, label: 'ecommerce-api', status: 'COMPLETED', created: '2025-03-09T14:05:00', count: 12 },
  { id: 2, label: 'payments-service', status: 'RUNNING', created: '2025-03-09T15:22:00', count: null },
  { id: 3, label: 'legacy-billing', status: 'DONE', created: '2025-03-08T17:10:00', count: 3 },
  { id: 4, label: 'reporting-etl', status: 'ERROR', created: '2025-02-28T08:14:00', count: null },
  { id: 5, label: 'identity-provider', status: 'ABGEBROCHEN', created: '2025-03-07T11:32:00', count: 0 }
];

let opened;
let removed;
let cancelled;
let deletedAll;

function mount(page) {
  const host = document.createElement('div');
  host.id = 'hxCard-' + page;
  host.className = 'hx-card';
  document.body.appendChild(host);
  return window.slHistory.mount({
    root: host,
    page: page,
    titleKey: 'common.history_title',
    emptyKey: 'common.history_empty',
    columns: {
      title: { key: 'code_analysis.col_repo', fallback: 'Repository' },
      metric: { key: 'common.history_col_findings', fallback: 'Befunde' }
    },
    map: {
      id: function(a) { return a.id; },
      status: function(a) { return a.status; },
      createdAt: function(a) { return a.created; },
      typeIcon: function() { return 'bi-repo'; },
      title: function(a) { return a.label; },
      metric: function(a) { return a.count == null ? '' : String(a.count); },
      note: function(a) { return a.error || ''; },
      progress: function(a) { return a.progress || null; },
      disabled: function(a) { return a.id == null; }
    },
    actions: {
      open: function(a) { opened.push(a.id); },
      remove: function(a) { removed.push(a.id); },
      cancel: function(a) { cancelled.push(a.id); },
      rerun: function(a) { removed.push('rerun:' + a.id); },
      deleteAll: function() { deletedAll.push(true); }
    }
  });
}

function cardEl() { return document.getElementById('hxCard-test'); }
function bodyRows() { return Array.from(cardEl().querySelectorAll('tbody tr.hx-row')); }

describe('slHistory (einheitlicher Verlauf-Baustein)', () => {
  beforeEach(() => {
    resetDom();
    document.body.innerHTML = '';
    window.i18n = { t: function(k) { return k; } };
    opened = []; removed = []; cancelled = []; deletedAll = [];
    loadScript(window, 'js/shared-helpers.js');
    loadScript(window, 'js/history-list.js');
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('normalisiert Backend-Enums auf die fünf erlaubten Zustände', () => {
    expect(window.slHistory.status('DONE')).toBe('COMPLETED');
    expect(window.slHistory.status('ERROR')).toBe('FAILED');
    expect(window.slHistory.status('ABGEBROCHEN')).toBe('CANCELLED');
    expect(window.slHistory.status('canceled')).toBe('CANCELLED');
    expect(window.slHistory.status('RUNNING')).toBe('RUNNING');
    expect(window.slHistory.status(undefined)).toBe('PENDING');
    expect(window.slHistory.isLive('PENDING')).toBe(true);
    expect(window.slHistory.isLive('COMPLETED')).toBe(false);
  });

  it('rendert genau eine Status-Pille pro Zeile', () => {
    mount('test').setData(ITEMS);
    expect(bodyRows()).toHaveLength(5);
    const badges = cardEl().querySelectorAll('tbody .hx-badge');
    expect(badges).toHaveLength(5);
    const seen = Array.from(badges).map(function(b) { return b.getAttribute('data-status'); });
    expect(seen).toEqual(['COMPLETED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']);
  });

  it('zeigt den Fehlerhinweis nur bei fehlgeschlagenen Einträgen', () => {
    const withErrors = ITEMS.map(function(a) {
      return Object.assign({}, a, { error: a.status === 'ERROR' ? 'Git-Clone fehlgeschlagen' : '' });
    });
    mount('test').setData(withErrors);
    const notes = cardEl().querySelectorAll('.hx-title-note');
    expect(notes).toHaveLength(1);
    expect(notes[0].textContent).toContain('Git-Clone fehlgeschlagen');
  });

  it('zeigt die Primäraktion immer und bedient Zeilenklick wie Button', () => {
    mount('test').setData(ITEMS);
    const first = bodyRows()[0];
    const openBtn = first.querySelector('[data-hx-act="open"]');
    expect(openBtn).toBeTruthy();
    expect(openBtn.textContent).toContain('Öffnen');
    openBtn.dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(opened).toEqual([1]);
    first.dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(opened).toEqual([1, 1]);
  });

  it('öffnet per Tastatur (Enter und Leertaste)', () => {
    mount('test').setData(ITEMS);
    const row = bodyRows()[0];
    expect(row.getAttribute('tabindex')).toBe('0');
    row.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    row.dispatchEvent(new window.KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    expect(opened).toEqual([1, 1]);
  });

  it('tauscht Löschen gegen Abbrechen bei laufenden Einträgen', () => {
    mount('test').setData(ITEMS);
    const running = bodyRows()[1];
    expect(running.querySelector('[data-hx-act="cancel"]')).toBeTruthy();
    expect(running.querySelector('[data-hx-act="remove"]')).toBeFalsy();
    expect(running.querySelector('[data-hx-act="open"]').textContent).toContain('Verfolgen');
    running.querySelector('[data-hx-act="cancel"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(cancelled).toEqual([2]);
    const done = bodyRows()[0];
    expect(done.querySelector('[data-hx-act="cancel"]')).toBeFalsy();
    expect(done.querySelector('[data-hx-act="remove"]')).toBeTruthy();
  });

  it('filtert über die Statuschips', () => {
    mount('test').setData(ITEMS);
    const chips = cardEl().querySelectorAll('[data-hx-filter]');
    expect(chips).toHaveLength(5);
    cardEl().querySelector('[data-hx-filter="COMPLETED"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(bodyRows()).toHaveLength(2);
    cardEl().querySelector('[data-hx-filter="RUNNING"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(bodyRows()).toHaveLength(1);
    cardEl().querySelector('[data-hx-filter="ALL"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(bodyRows()).toHaveLength(5);
  });

  it('sucht über Titel, Unterzeile und Fehlerhinweis', () => {
    mount('test').setData(ITEMS.map(function(a) {
      return Object.assign({}, a, { error: a.id === 4 ? 'permission denied' : '' });
    }));
    const input = cardEl().querySelector('[data-hx-search]');
    input.value = 'legacy';
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    expect(bodyRows()).toHaveLength(1);
    expect(bodyRows()[0].textContent).toContain('legacy-billing');
    input.value = 'permission';
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    expect(bodyRows()).toHaveLength(1);
    expect(bodyRows()[0].textContent).toContain('reporting-etl');
  });

  it('merkt sich Offen/Zugemerkt pro Seite in einem einheitlichen Key', () => {
    const controller = mount('test');
    controller.setData(ITEMS);
    const head = cardEl().querySelector('[data-hx-toggle]');
    expect(window.localStorage.getItem('stacklens.history.open.test')).toBe('1');
    head.dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(cardEl().classList.contains('is-collapsed')).toBe(true);
    expect(window.localStorage.getItem('stacklens.history.open.test')).toBe('0');
    expect(head.getAttribute('aria-expanded')).toBe('false');
    document.body.innerHTML = '';
    expect(mount('test').element.classList.contains('is-collapsed')).toBe(true);
  });

  it('zeigt den Leerzustand und verbirgt „Alle löschen“ ohne Einträge', () => {
    const controller = mount('test');
    controller.setData([]);
    expect(cardEl().querySelector('[data-hx-empty]').hidden).toBe(false);
    expect(cardEl().querySelector('table').hidden).toBe(true);
    expect(cardEl().querySelector('[data-hx-delall]').hidden).toBe(true);
    controller.setData(ITEMS);
    expect(cardEl().querySelector('[data-hx-empty]').hidden).toBe(true);
    expect(cardEl().querySelector('[data-hx-delall]').hidden).toBe(false);
    cardEl().querySelector('[data-hx-delall]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(deletedAll).toHaveLength(1);
  });

  it('rendert das Live-Band mit Stufe, Prozent und Abbrechen', () => {
    const live = [{ id: 9, label: 'running-run', status: 'RUNNING', created: '2025-03-09T15:22:00', count: null, progress: { pct: 62, stage: 'Schritt 2/3' } }];
    mount('test').setData(live);
    const band = cardEl().querySelector('.hx-progress-row');
    expect(band).toBeTruthy();
    expect(band.textContent).toContain('Schritt 2/3');
    expect(band.textContent).toContain('62%');
    expect(band.querySelector('.hx-progress-fill').getAttribute('style')).toContain('width:62%');
    expect(band.querySelector('[data-hx-act="cancel"]')).toBeTruthy();
  });

  it('blendet das Live-Band bei unbekannter Prozentzahl als unbestimmten Balken', () => {
    const live = [{ id: 9, label: 'running-run', status: 'RUNNING', created: '2025-03-09T15:22:00', progress: { pct: null, stage: 'Sammle Dateien' } }];
    mount('test').setData(live);
    expect(cardEl().querySelector('.hx-progress-stripe')).toBeTruthy();
    expect(cardEl().querySelector('.hx-progress-pct')).toBeFalsy();
  });

  it('markiert den geöffneten Eintrag und aktualisiert die Markierung über refresh', () => {
    let current = 2;
    const host = document.createElement('div');
    host.id = 'hxCard-current';
    host.className = 'hx-card';
    document.body.appendChild(host);
    const controller = window.slHistory.mount({
      root: host,
      page: 'current',
      titleKey: 'common.history_title',
      emptyKey: 'common.history_empty',
      columns: { title: { key: 'code_analysis.col_repo', fallback: 'Repository' } },
      map: {
        id: function(a) { return a.id; },
        status: function(a) { return a.status; },
        createdAt: function(a) { return a.created; },
        title: function(a) { return a.label; },
        isCurrent: function(a) { return a.id === current; }
      },
      actions: { open: function(a) { opened.push(a.id); } }
    });
    controller.setData(ITEMS);
    const marked = function() {
      return Array.from(host.querySelectorAll('tbody tr.hx-row.is-current')).map(function(r) { return r.dataset.hxIndex; });
    };
    expect(marked()).toEqual(['1']);
    current = 4;
    controller.refresh();
    expect(marked()).toEqual(['3']);
    current = null;
    controller.refresh();
    expect(marked()).toEqual([]);
  });

  it('deaktiviert Zeilen ohne Server-Identität (Live-Zeile vor dem Persistieren)', () => {
    mount('test').setData([{ id: null, label: 'SELECT 1', status: 'RUNNING', created: '2025-03-09T15:30:00' }]);
    const row = bodyRows()[0];
    expect(row.classList.contains('is-disabled')).toBe(true);
    expect(row.getAttribute('tabindex')).toBe(null);
    expect(row.querySelector('[data-hx-act="open"]').disabled).toBe(true);
  });

  it('escaped Titel, Fehlerhinweis und title-Attribute', () => {
    mount('test').setData([{
      id: 1,
      label: '" onmouseover="alert(1)"><img src=x>',
      status: 'FAILED',
      created: '2025-03-09T15:22:00',
      error: '</td><td>injiziert'
    }]);
    expect(cardEl().querySelector('img')).toBe(null);
    expect(cardEl().querySelector('[onmouseover]')).toBe(null);
    expect(cardEl().querySelector('table table')).toBe(null);
    const titleCell = cardEl().querySelector('.hx-title-text');
    expect(titleCell.textContent).toBe('" onmouseover="alert(1)"><img src=x>');
    expect(titleCell.getAttribute('title')).toBe('" onmouseover="alert(1)"><img src=x>');
    expect(cardEl().querySelector('.hx-title-note').textContent).toBe('</td><td>injiziert');
    expect(cardEl().querySelectorAll('tbody tr')).toHaveLength(1);
    expect(cardEl().querySelectorAll('tbody td')).toHaveLength(5);
  });

  it('indiziert Zeilen stabil, auch wenn Filter und Daten sich unterscheiden', () => {
    mount('test').setData(ITEMS);
    cardEl().querySelector('[data-hx-filter="FAILED"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    bodyRows()[0].querySelector('[data-hx-act="remove"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(removed).toEqual([4]);
  });
});

describe('slHistory.createLiveSync', () => {
  beforeEach(() => {
    resetDom();
    document.body.innerHTML = '';
    loadScript(window, 'js/history-list.js');
  });

  it('meldet jeden neuen Live-Zustand genau einmal', () => {
    let calls = 0;
    const sync = window.slHistory.createLiveSync({ reload: () => { calls += 1; } });
    expect(sync(null)).toBe(false);
    expect(sync({ id: 7, status: 'PENDING' })).toBe(true);
    expect(sync({ id: 7, status: 'PENDING' })).toBe(false);
    expect(sync({ id: 7, status: 'RUNNING' })).toBe(true);
    expect(sync({ id: 8, status: 'RUNNING' })).toBe(true);
    expect(calls).toBe(3);
  });

  it('kennt Alias-Zustände als denselben Live-Zustand', () => {
    let calls = 0;
    const sync = window.slHistory.createLiveSync({ reload: () => { calls += 1; } });
    expect(sync({ id: 7, status: 'RUNNING' })).toBe(true);
    expect(sync({ id: 7, status: 'IN_PROGRESS' })).toBe(false);
    expect(sync({ id: 7, status: 'STARTED' })).toBe(false);
    expect(calls).toBe(1);
  });

  it('erweitert die Signatur um die Fortschrittsfelder der Seite', () => {
    let calls = 0;
    const sync = window.slHistory.createLiveSync({
      reload: () => { calls += 1; },
      fields: ['currentIteration', 'currentStep']
    });
    expect(sync({ id: 7, status: 'RUNNING', currentIteration: 1, currentStep: 'A' })).toBe(true);
    expect(sync({ id: 7, status: 'RUNNING', currentIteration: 1, currentStep: 'A' })).toBe(false);
    expect(sync({ id: 7, status: 'RUNNING', currentIteration: 2, currentStep: 'A' })).toBe(true);
    expect(sync({ id: 7, status: 'RUNNING', currentIteration: 2, currentStep: 'B' })).toBe(true);
    expect(calls).toBe(3);
  });
});
