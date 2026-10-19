import { describe, it, expect, afterEach } from 'vitest';
import {
  bootConfigIo, browserModal, browserRows, flush, rowByText, rowLabels, elementById, readResponse, deferred,
} from './support/configIoHarness.js';

describe('config import uses the shared file browser', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  it('opens the shared browser in single-file mode', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    started.win.openImportModal();
    await flush();
    expect(browserModal(started.win)).toBeTruthy();
    expect(elementById(started.win, 'kiDbPick').style.display).toBe('none');
    expect(elementById(started.win, 'kiDbFilename').style.display).toBe('none');
  });

  it('asks the tooling endpoint for json files only', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    started.win.openImportModal();
    await flush();
    expect(started.calls[0]).toContain('/api/tooling/files/browse');
    expect(started.calls[0]).toContain('fileExtensions=json');
  });

  it('names json as the accepted type in the footer', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    started.win.openImportModal();
    await flush();
    expect(elementById(started.win, 'kiDbFooterLabel').textContent).toContain('JSON');
  });

  it('lists the json files of the current folder', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    started.win.openImportModal();
    await flush();
    expect(rowLabels(started.win).some((label) => label.includes('configs'))).toBe(true);
    rowByText(started.win, 'configs').click();
    await flush();
    const labels = rowLabels(started.win);
    expect(labels.some((label) => label.includes('stacklens-config.json'))).toBe(true);
    expect(labels.some((label) => label.includes('/Users/me'))).toBe(true);
  });

  it('reads the picked file and hands content plus name to the confirm dialog', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    const confirmed = [];
    win.cfOpenImportConfirm = (content, filename) => { confirmed.push([content, filename]); };
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    rowByText(win, 'stacklens-config.json').click();
    await flush();
    expect(started.calls.some((url) => url.includes('/api/prompts/read-file?path=%2FUsers%2Fme%2Fconfigs%2Fstacklens-config.json'))).toBe(true);
    expect(confirmed).toEqual([['{"gitlab":[1]}', 'stacklens-config.json']]);
    expect(win.__cfImportFilePath).toBe('/Users/me/configs/stacklens-config.json');
    expect(browserModal(win)).toBeNull();
  });

  it('keeps the browser open while the config file is read and closes it once the dialog is up', async () => {
    const pending = deferred();
    const started = bootConfigIo({ readFile: () => pending.promise });
    dom = started.dom;
    const win = started.win;
    const opened = [];
    win.cfOpenImportConfirm = (content, filename) => {
      opened.push({ content, filename, browserStillOpen: !!browserModal(win) });
    };
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    rowByText(win, 'stacklens-config.json').click();
    await flush();
    expect(browserModal(win)).toBeTruthy();
    expect(opened).toEqual([]);
    pending.resolve(readResponse({ content: '{"gitlab":[1]}' }));
    await flush();
    expect(opened).toEqual([{
      content: '{"gitlab":[1]}',
      filename: 'stacklens-config.json',
      browserStillOpen: true,
    }]);
    expect(browserModal(win)).toBeNull();
  });

  it('ignores a second activation of a row while the read is still running', async () => {
    const pending = deferred();
    const started = bootConfigIo({ readFile: () => pending.promise });
    dom = started.dom;
    const win = started.win;
    win.cfOpenImportConfirm = () => { };
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    rowByText(win, 'stacklens-config.json').click();
    rowByText(win, 'stacklens-config.json').click();
    await flush();
    expect(started.calls.filter((url) => url.includes('/api/prompts/read-file'))).toHaveLength(1);
    pending.resolve(readResponse({ content: '{"gitlab":[1]}' }));
    await flush();
    expect(browserModal(win)).toBeNull();
  });

  it('closes the browser when the read fails and reports the toast', async () => {
    const started = bootConfigIo({ readFile: () => Promise.reject(new Error('boom')) });
    dom = started.dom;
    const win = started.win;
    const confirmed = [];
    win.cfOpenImportConfirm = () => { confirmed.push('opened'); };
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    rowByText(win, 'stacklens-config.json').click();
    await flush();
    expect(confirmed).toEqual([]);
    expect(started.alerts).toEqual([['Netzwerkfehler: boom', 'danger']]);
    expect(browserModal(win)).toBeNull();
  });

  it('reports a read error as a toast and never opens the confirm dialog', async () => {
    const started = bootConfigIo({ readFileBody: { error: 'Zugriff verweigert' } });
    dom = started.dom;
    const win = started.win;
    const confirmed = [];
    win.cfOpenImportConfirm = () => { confirmed.push('opened'); };
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    rowByText(win, 'stacklens-config.json').click();
    await flush();
    expect(confirmed).toEqual([]);
    expect(started.alerts).toEqual([['Zugriff verweigert', 'danger']]);
  });

  it('keeps the browser closed state untouched when cancelled', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    const confirmed = [];
    win.cfOpenImportConfirm = () => { confirmed.push('opened'); };
    win.openImportModal();
    await flush();
    elementById(win, 'kiDbCancel').click();
    await flush();
    expect(confirmed).toEqual([]);
    expect(browserModal(win)).toBeNull();
  });

  it('remembers the last folder for the next opening', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    win.openImportModal();
    await flush();
    rowByText(win, 'configs').click();
    await flush();
    elementById(win, 'kiDbCancel').click();
    await flush();
    win.openImportModal();
    await flush();
    expect(elementById(win, 'kiDbPath').value).toBe('/Users/me/configs');
    expect(win.localStorage.getItem('stacklens.fb.last.configImport')).toBe('/Users/me/configs');
  });

  it('ships no rows other than the shared browser list', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    started.win.openImportModal();
    await flush();
    expect(browserRows(started.win).length).toBeGreaterThan(0);
    expect(started.win.document.querySelectorAll('[id^="cfImportBrowser"]').length).toBe(0);
  });
});
