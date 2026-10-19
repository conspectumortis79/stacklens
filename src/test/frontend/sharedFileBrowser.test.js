import { describe, it, expect, afterEach } from 'vitest';
import { bootSharedBrowser, flush, rowByText } from './support/sharedBrowserHarness.js';

function pickButton(win) {
  return win.document.getElementById('kiDbPick');
}

function modal(win) {
  return win.document.getElementById('kiChatDirBrowserModal');
}

describe('shared file browser — directory mode', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  const dirResponses = [{
    match: 'list-directory',
    body: { current: '/tmp', parent: '/', directories: [{ name: 'src', path: '/tmp/src' }] },
  }];

  it('loads through the local-repos endpoint and enables the folder pick button', async () => {
    const boot = bootSharedBrowser(dirResponses);
    dom = boot.dom;
    let picked = 'untouched';
    boot.win.__kiChatOpenDirBrowser((path) => { picked = path; });
    await flush();

    expect(boot.calls[0]).toContain('/api/local-repos/list-directory');
    expect(pickButton(boot.win).style.display).not.toBe('none');
    expect(pickButton(boot.win).disabled).toBe(false);
    expect(rowByText(boot.win, 'src')).toBeTruthy();

    pickButton(boot.win).click();
    expect(picked).toBe('/tmp');
  });

  it('navigates into a subdirectory on click', async () => {
    const boot = bootSharedBrowser(dirResponses);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { });
    await flush();

    rowByText(boot.win, 'src').click();
    await flush();
    expect(boot.calls[1]).toContain('path=%2Ftmp%2Fsrc');
  });

  it('closes without a result on cancel', async () => {
    const boot = bootSharedBrowser(dirResponses);
    dom = boot.dom;
    let picked = 'untouched';
    boot.win.__kiChatOpenDirBrowser((path) => { picked = path; });
    await flush();

    boot.win.document.getElementById('kiDbCancel').click();
    expect(picked).toBe('');
    expect(modal(boot.win)).toBeNull();
  });
});

describe('shared file browser — injected loader and start path', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  it('uses the injected loader instead of the local-repos endpoint', async () => {
    const seen = [];
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      load: (path) => {
        seen.push(path);
        return Promise.resolve({ current: '/loaded', parent: null, directories: [], files: [] });
      },
    });
    await flush();
    expect(seen).toEqual(['']);
    expect(boot.calls).toHaveLength(0);
    expect(boot.win.document.getElementById('kiDbCurrent').textContent).toBe('/loaded');
  });

  it('opens at the requested start path', async () => {
    const seen = [];
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      startPath: '/tmp/x',
      load: (path) => {
        seen.push(path);
        return Promise.resolve({ current: path, parent: '/tmp', directories: [], files: [] });
      },
    });
    await flush();
    expect(seen).toEqual(['/tmp/x']);
    expect(boot.win.document.getElementById('kiDbPath').value).toBe('/tmp/x');
  });

  it('surfaces a loader error inside the list instead of throwing', async () => {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      load: () => Promise.reject(new Error('boom')),
    });
    await flush();
    expect(boot.win.document.getElementById('kiDbList').textContent).toContain('boom');
  });
});

describe('shared file browser — file mode', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  const entries = {
    current: '/Users/me/decks',
    parent: '/Users/me',
    directories: [{ name: 'archive', path: '/Users/me/decks/archive' }],
    files: [{ name: 'sprint-review.pptx', path: '/Users/me/decks/sprint-review.pptx' }],
  };

  function openFileMode(seen) {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    let picked = 'untouched';
    boot.win.__kiChatOpenDirBrowser((path) => { picked = path; }, {
      files: ['ppt', 'pptx'],
      titleKey: 'review_analysis.pptx_browser_title',
      titleFallback: 'PPTX-Datei wählen',
      load: (path) => {
        seen.push(path);
        return Promise.resolve(entries);
      },
    });
    return { win: boot.win, picked: () => picked };
  }

  it('titles the dialog with the caller label and hides the folder pick button', async () => {
    const { win } = openFileMode([]);
    await flush();
    expect(modal(win).textContent).toContain('PPTX-Datei wählen');
    expect(pickButton(win).style.display).toBe('none');
    expect(win.document.getElementById('kiDbFooterLabel').textContent).toContain('Datei antippen');
  });

  it('resolves with the clicked file path and closes', async () => {
    const { win, picked } = openFileMode([]);
    await flush();
    rowByText(win, 'sprint-review.pptx').click();
    expect(picked()).toBe('/Users/me/decks/sprint-review.pptx');
    expect(modal(win)).toBeNull();
  });

  it('navigates folders without resolving them', async () => {
    const seen = [];
    openFileMode(seen);
    await flush();
    rowByText(dom.window, 'archive').click();
    await flush();
    expect(seen).toEqual(['', '/Users/me/decks/archive']);
  });

  it('offers no local-computer upload control', async () => {
    const { win } = openFileMode([]);
    await flush();
    expect(modal(win).textContent).not.toContain('Vom Computer');
    expect(modal(win).querySelector('input[type=file]')).toBeNull();
  });

  it('shows the file hint when a folder holds no matching file', async () => {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      files: ['pptx'],
      load: () => Promise.resolve({ current: '/empty', parent: null, directories: [], files: [] }),
    });
    await flush();
    expect(boot.win.document.getElementById('kiDbList').textContent).toContain('keine passenden Dateien');
  });
});

describe('shared file browser — save-as mode', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  function openSaveAs(filename) {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    const results = [];
    boot.win.__kiChatOpenDirBrowser((path, name) => { results.push([path, name]); }, {
      saveAs: { filename },
      titleFallback: 'Speicherort wählen',
      load: () => Promise.resolve({
        current: '/tmp/out',
        parent: '/tmp',
        directories: [{ name: 'sub', path: '/tmp/out/sub' }],
        files: [{ name: 'stray.ki', path: '/tmp/out/stray.ki' }],
      }),
    });
    return { win: boot.win, results };
  }

  it('shows a pre-filled filename field and a save button', async () => {
    const { win } = openSaveAs('ki-prompt-review.ki');
    await flush();
    const input = win.document.getElementById('kiDbFilename');
    expect(input.style.display).toBe('');
    expect(input.value).toBe('ki-prompt-review.ki');
    expect(pickButton(win).textContent).toContain('Hier speichern');
  });

  it('reports the directory together with the trimmed filename', async () => {
    const { win, results } = openSaveAs('a.ki');
    await flush();
    win.document.getElementById('kiDbFilename').value = '  renamed.ki  ';
    pickButton(win).click();
    expect(results).toEqual([['/tmp/out', 'renamed.ki']]);
  });

  it('lists directories only — files never appear in save mode', async () => {
    const { win } = openSaveAs('a.ki');
    await flush();
    const text = win.document.getElementById('kiDbList').textContent;
    expect(text).toContain('sub');
    expect(text).not.toContain('stray.ki');
  });
});

describe('shared file browser — multi-select mode', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  const entries = {
    current: '/tmp/mix',
    parent: '/tmp',
    directories: [],
    files: [
      { name: 'a.png', path: '/tmp/mix/a.png' },
      { name: 'b.png', path: '/tmp/mix/b.png' },
    ],
  };

  function openMulti() {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    const results = [];
    boot.win.__kiChatOpenDirBrowser((paths) => { results.push(paths); }, {
      files: ['png'],
      multi: true,
      titleFallback: 'Mockups wählen',
      load: () => Promise.resolve(entries),
    });
    return { win: boot.win, results };
  }

  it('keeps an apply button and a multi hint in the footer', async () => {
    const { win } = openMulti();
    await flush();
    expect(pickButton(win).textContent).toContain('Übernehmen');
    expect(win.document.getElementById('kiDbFooterLabel').textContent).toContain('Auswählen / Abwählen');
  });

  it('toggles files without closing and applies the selection once', async () => {
    const { win, results } = openMulti();
    await flush();

    rowByText(win, 'a.png').click();
    expect(results).toHaveLength(0);
    expect(modal(win)).not.toBeNull();

    rowByText(win, 'b.png').click();
    rowByText(win, 'a.png').click();
    pickButton(win).click();
    expect(results).toEqual([['/tmp/mix/b.png']]);
  });

  it('applies an empty selection as an empty array', async () => {
    const { win, results } = openMulti();
    await flush();
    pickButton(win).click();
    expect(results).toEqual([[]]);
  });

  it('marks already selected rows so a re-render keeps the state', async () => {
    const { win } = openMulti();
    await flush();
    rowByText(win, 'a.png').click();
    await flush();
    const selected = rowByText(win, 'a.png');
    const unselected = rowByText(win, 'b.png');
    expect(selected).toBeTruthy();
    expect(selected.style.background).not.toBe(unselected.style.background);
    expect(selected.style.background).toContain('244');
  });

  it('starts from a caller supplied selection in multi mode', async () => {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    const results = [];
    boot.win.__kiChatOpenDirBrowser((paths) => { results.push(paths); }, {
      files: ['png'],
      multi: true,
      selected: ['/tmp/mix/a.png'],
      load: () => Promise.resolve({
        current: '/tmp/mix',
        parent: null,
        directories: [],
        files: [
          { name: 'a.png', path: '/tmp/mix/a.png' },
          { name: 'b.png', path: '/tmp/mix/b.png' },
        ],
      }),
    });
    await flush();
    pickButton(boot.win).click();
    expect(results).toEqual([['/tmp/mix/a.png']]);
  });

  it('calls onToggle with the new selection state on every click', async () => {
    const seen = [];
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      files: ['png'],
      multi: true,
      onToggle: (path, name, isSelected) => { seen.push([path, name, isSelected]); },
      load: () => Promise.resolve({
        current: '/tmp/mix',
        parent: null,
        directories: [],
        files: [{ name: 'a.png', path: '/tmp/mix/a.png' }],
      }),
    });
    await flush();
    rowByText(boot.win, 'a.png').click();
    await flush();
    rowByText(boot.win, 'a.png').click();
    expect(seen).toEqual([
      ['/tmp/mix/a.png', 'a.png', true],
      ['/tmp/mix/a.png', 'a.png', false],
    ]);
  });

  it('renders a caller supplied description next to each file', async () => {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(() => { }, {
      files: ['png'],
      describe: (file) => (file.size ? Math.round(file.size / 1024) + ' KB' : 'unbekannt'),
      load: () => Promise.resolve({
        current: '/tmp/mix',
        parent: null,
        directories: [],
        files: [
          { name: 'big.png', path: '/tmp/mix/big.png', size: 2048 },
          { name: 'quiet.png', path: '/tmp/mix/quiet.png' },
        ],
      }),
    });
    await flush();
    const text = boot.win.document.getElementById('kiDbList').textContent;
    expect(text).toContain('2 KB');
    expect(text).toContain('unbekannt');
  });
});

describe('shared file browser — awaited selection', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  const entries = {
    current: '/tmp/pick',
    parent: '/tmp',
    directories: [],
    files: [{ name: 'a.json', path: '/tmp/pick/a.json' }],
  };

  function open(awaitSelection, onSelect) {
    const boot = bootSharedBrowser([]);
    dom = boot.dom;
    boot.win.__kiChatOpenDirBrowser(onSelect, {
      files: ['json'],
      awaitSelection,
      load: () => Promise.resolve(entries),
    });
    return boot.win;
  }

  function deferredPromise() {
    let resolve;
    let reject;
    const promise = new Promise((resolveNext, rejectNext) => {
      resolve = resolveNext;
      reject = rejectNext;
    });
    return { promise, resolve, reject };
  }

  it('still removes itself before the callback runs when the caller does not await', async () => {
    const pending = deferredPromise();
    const mountedAtCallback = [];
    const win = open(false, () => {
      mountedAtCallback.push(!!modal(win));
      return pending.promise;
    });
    await flush();
    rowByText(win, 'a.json').click();
    expect(mountedAtCallback).toEqual([false]);
    pending.resolve();
    await flush();
    expect(modal(win)).toBeNull();
  });

  it('stays mounted while the awaited selection is running and closes once it settles', async () => {
    const pending = deferredPromise();
    const picked = [];
    const win = open(true, (path) => {
      picked.push(path);
      return pending.promise;
    });
    await flush();
    rowByText(win, 'a.json').click();
    await flush();
    expect(picked).toEqual(['/tmp/pick/a.json']);
    expect(modal(win)).toBeTruthy();
    pending.resolve();
    await flush();
    expect(modal(win)).toBeNull();
  });

  it('closes immediately when the awaited callback returns no promise', async () => {
    const win = open(true, () => '');
    await flush();
    rowByText(win, 'a.json').click();
    expect(modal(win)).toBeNull();
  });

  it('closes when the awaited selection rejects', async () => {
    const win = open(true, () => Promise.reject(new Error('nope')));
    await flush();
    rowByText(win, 'a.json').click();
    await flush();
    expect(modal(win)).toBeNull();
  });

  it('ignores a second activation while the selection is still running', async () => {
    const pending = deferredPromise();
    const picked = [];
    const win = open(true, (path) => {
      picked.push(path);
      return pending.promise;
    });
    await flush();
    rowByText(win, 'a.json').click();
    rowByText(win, 'a.json').click();
    await flush();
    expect(picked).toHaveLength(1);
    expect(modal(win)).toBeTruthy();
    pending.resolve();
    await flush();
    expect(modal(win)).toBeNull();
    expect(picked).toHaveLength(1);
  });

  it('cancelling an awaited browser never keeps it mounted', async () => {
    const win = open(true, () => undefined);
    await flush();
    win.document.getElementById('kiDbCancel').click();
    expect(modal(win)).toBeNull();
  });
});
