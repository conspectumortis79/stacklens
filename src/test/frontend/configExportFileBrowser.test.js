import { describe, it, expect, afterEach } from 'vitest';
import {
  bootConfigIo, browserModal, flush, rowByText, elementById,
} from './support/configIoHarness.js';

const EXPORT_FILENAME = 'stacklens-config-2026-08-31.json';
const EXPORT_CONTENT = '{"gitlab":[{"name":"prod"}],"encrypted":false}';

function openSaveBrowser(win, filename = EXPORT_FILENAME, content = EXPORT_CONTENT) {
  win.cfOpenSaveBrowser(filename, content);
}

async function openAtConfigs(win) {
  openSaveBrowser(win);
  await flush();
  rowByText(win, 'configs').click();
  await flush();
}

describe('config export uses the shared file browser', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  it('opens the shared browser in save-as mode with the derived filename', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    openSaveBrowser(started.win);
    await flush();
    expect(browserModal(started.win)).toBeTruthy();
    expect(elementById(started.win, 'kiDbFilename').value).toBe(EXPORT_FILENAME);
    expect(elementById(started.win, 'kiDbFilename').style.display).toBe('');
    expect(elementById(started.win, 'kiDbPick').style.display).not.toBe('none');
    expect(elementById(started.win, 'kiDbPick').textContent).toContain('Hier speichern');
    expect(started.win.document.querySelectorAll('[id^="cfSaveBrowser"]').length).toBe(0);
  });

  it('asks the tooling endpoint for directories only', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    openSaveBrowser(started.win);
    await flush();
    expect(started.calls[0]).toContain('/api/tooling/files/browse');
    expect(started.calls[0]).toContain('directoriesOnly=true');
    expect(started.calls[0]).not.toContain('fileExtensions');
  });

  it('saves the exported text to the picked folder with the edited filename', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    await openAtConfigs(win);
    elementById(win, 'kiDbFilename').value = 'my-config.json';
    elementById(win, 'kiDbPick').click();
    await flush();
    expect(started.saveRequests).toEqual([{
      path: '/Users/me/configs',
      filename: 'my-config.json',
      content: EXPORT_CONTENT,
    }]);
    expect(started.alerts).toEqual([['Konfiguration gespeichert: /Users/me/configs/stacklens-config.json', 'success']]);
    expect(browserModal(win)).toBeNull();
  });

  it('refuses to save without a filename', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    await openAtConfigs(win);
    elementById(win, 'kiDbFilename').value = '   ';
    elementById(win, 'kiDbPick').click();
    await flush();
    expect(started.saveRequests).toEqual([]);
    expect(started.alerts).toEqual([['Dateiname darf nicht leer sein.', 'danger']]);
  });

  it('reports a server side rejection as a danger toast', async () => {
    const started = bootConfigIo({ saveBody: { error: 'Pfad liegt außerhalb des Home-Verzeichnisses' } });
    dom = started.dom;
    const win = started.win;
    await openAtConfigs(win);
    elementById(win, 'kiDbPick').click();
    await flush();
    expect(started.saveRequests).toHaveLength(1);
    expect(started.alerts).toEqual([['Pfad liegt außerhalb des Home-Verzeichnisses', 'danger']]);
  });

  it('reports a network failure as a danger toast', async () => {
    const started = bootConfigIo({ saveFailure: 'fetch failed' });
    dom = started.dom;
    const win = started.win;
    await openAtConfigs(win);
    elementById(win, 'kiDbPick').click();
    await flush();
    expect(started.alerts).toEqual([['Netzwerkfehler: fetch failed', 'danger']]);
  });

  it('does not save anything when cancelled', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    openSaveBrowser(win);
    await flush();
    elementById(win, 'kiDbCancel').click();
    await flush();
    expect(started.saveRequests).toEqual([]);
    expect(browserModal(win)).toBeNull();
  });

  it('remembers the picked folder under its own storage key', async () => {
    const started = bootConfigIo();
    dom = started.dom;
    const win = started.win;
    await openAtConfigs(win);
    elementById(win, 'kiDbCancel').click();
    await flush();
    openSaveBrowser(win);
    await flush();
    expect(elementById(win, 'kiDbPath').value).toBe('/Users/me/configs');
    expect(win.localStorage.getItem('stacklens.fb.last.configExport')).toBe('/Users/me/configs');
    elementById(win, 'kiDbCancel').click();
    await flush();
    win.openImportModal();
    await flush();
    expect(elementById(win, 'kiDbPath').value).toBe('/Users/me');
  });
});
