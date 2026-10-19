import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';


const __dirname = dirname(fileURLToPath(import.meta.url));
const PAGE = resolve(__dirname, '../../main/resources/templates/docker-test.html');

const PAGE_DOM = `
  <main>
    <span id="dtStatusBadge" class="dt-status-badge dt-status-idle">
      <span class="dt-spinner" id="dtSpinner" style="display:none"></span>
      <span id="dtStatusText" data-i18n="docker_test.status_idle">Bereit</span>
    </span>
    <div id="dtComposeInfo" style="display:none"><span id="dtComposeInfoText"></span></div>
    <div id="dtComposeNotFound" style="display:none"></div>
    <div id="dtDockerNotAvailable" style="display:none"></div>
    <div id="dtRestartProgress" style="display:none"></div>
    <div id="dtDockerDiagnostic" style="display:none"></div>
    <div id="dtOutput" class="dt-output-box"></div>
    <button id="dtStartBtn" class="dt-btn dt-btn-primary"></button>
    <button id="dtResetBtn" class="dt-btn dt-btn-danger"></button>
    <div id="dtFileList"><div class="dt-file-msg"></div></div>
    <textarea id="dtEditorTextarea" class="dt-editor-textarea" disabled></textarea>
    <div id="dtEditorOverlay" class="dt-editor-overlay" style="display:none"></div>
    <div id="alertArea"></div>
  </main>
`;

function loadScripts(win) {
  runInWindow(win, extractInlineScripts(PAGE));
}

function badge(win) {
  return {
    className: win.document.getElementById('dtStatusBadge').className,
    text: win.document.getElementById('dtStatusText').textContent,
    spinnerVisible: win.document.getElementById('dtSpinner').style.display !== 'none',
    startDisabled: win.document.getElementById('dtStartBtn').disabled,
    outputText: (win.document.getElementById('dtOutput').textContent || '').replace(/\n$/, ''),
  };
}

function boot() {
  const win = makeWindow(PAGE_DOM);
  win.i18n = { t: (k) => k };
  win.showAlert = () => {};
  win.showConfirm = (_msg, _title, onOk) => { try { onOk && onOk(); } catch (_) {} };
  win.SLScroll = () => ({ update: () => {} });

  win.__emitters = [];
  win.__attachPolls = 0;
  win.__attachClosed = false;
  win.__state = {
    jobId: null,
    status: 'idle',
    output: [],
    lineCount: 0,
  };
  win.__attachQueue = [];

  class FakeEventSource {
    constructor(url) {
      this.url = url;
      this.readyState = 1;
      this.onmessage = null;
      this.onerror = null;
      win.__emitters.push(this);
      if (typeof url === 'string' && url.indexOf('/api/docker-test/attach') !== -1) {
        win.__attachPolls++;
        const u = new URL(url, 'http://x');
        const since = parseInt(u.searchParams.get('since') || '0', 10);
        win.__activeAttach = this;
        const deliverNext = () => {
          const batch = win.__attachQueue.shift();
          if (!batch) return;
          for (const line of batch.lines || []) this.send(line);
          if (batch.final) {
            this.send(batch.final);
            this.close();
            win.__attachClosed = true;
            return;
          }
          setTimeout(deliverNext, 5);
        };
        setTimeout(deliverNext, 5);
      } else if (typeof url === 'string' && (url.indexOf('/api/docker-test/start') !== -1 || url.indexOf('/api/docker-test/reset') !== -1)) {
      }
    }
    send(line) { if (this.onmessage) this.onmessage({ data: line }); }
    fail() { if (this.onerror) this.onerror(); }
    close() { this.readyState = 2; }
  }
  win.EventSource = FakeEventSource;

  win.fetch = (url) => {
    if (url.startsWith('/api/docker-test/info')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          composeFile: '/tmp/docker-compose.yml',
          found: 'true',
          dockerAvailable: 'true',
          dockerDiagnostic: '',
        }),
      });
    }
    if (url === '/api/docker-test/data-files') {
      return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
    }
    if (url.startsWith('/api/docker-test/state')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          jobId: win.__state.jobId,
          status: win.__state.status,
          output: win.__state.output.slice(),
          lineCount: win.__state.lineCount,
        }),
      });
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
  };

  return win;
}

async function flush(win, n = 10) {
  for (let i = 0; i < n; i++) await new Promise((r) => setTimeout(r, 0));
}

describe('Testdocker: status + terminal output reflect reality on navigate-back', () => {
  let win;

  beforeEach(() => {
    win = boot();
    loadScripts(win);
  });

  afterEach(() => {
    if (win && win.__emitters) win.__emitters.forEach((e) => e.close());
  });

  it('sanity: page loads with idle badge and empty terminal', () => {
    const b = badge(win);
    expect(b.className).toContain('dt-status-idle');
    expect(b.text).toBe('Bereit');
    expect(b.spinnerVisible).toBe(false);
  });

  it('cold page: /state with no current job shows idle (no attach SSE opened)', async () => {
    win.__state = {
      jobId: null,
      status: 'idle',
      output: [],
      lineCount: 0,
    };
    win.dtInit && win.dtInit();
    const b = badge(win);
    expect(b.className).toContain('dt-status-idle');
    expect(b.outputText).toBe('');
  });

  it('navigate-back during RUNNING build → badge is "Läuft…" + full output shown + attach SSE opened', async () => {
    win.__state = {
      jobId: 'job-abc',
      status: 'running',
      output: [
        '#1 [internal] load local bake definitions',
        '#1 reading from stdin 2.83kB done',
        'Image stacklens/fake-exporter:local Building',
      ],
      lineCount: 3,
    };
    win.__attachQueue.push({
      lines: ['#2 CACHED', '#3 CACHED'],
      final: null,
    });
    win.__attachQueue.push({ lines: [], final: null });

    loadScripts(win);
    await flush(win, 30);

    const b = badge(win);
    expect(b.className).toContain('dt-status-running');
    expect(b.spinnerVisible).toBe(true);
    expect(b.outputText).toContain('Image stacklens/fake-exporter:local Building');
    expect(b.outputText).toContain('#2 CACHED');
    expect(b.outputText).toContain('#3 CACHED');
    expect(win.__attachPolls).toBeGreaterThan(0);
  });

  it('navigate-back AFTER the build finished → badge shows DONE with full terminal', async () => {
    win.__state = {
      jobId: 'job-done',
      status: 'done',
      output: ['#1 DONE 0.1s', 'Container started'],
      lineCount: 2,
    };
    loadScripts(win);
    await flush(win, 30);

    const b = badge(win);
    expect(b.className).toContain('dt-status-done');
    expect(b.spinnerVisible).toBe(false);
    expect(b.outputText).toContain('#1 DONE 0.1s');
    expect(b.outputText).toContain('Container started');
    expect(b.startDisabled).toBe(false);
    expect(b.text).toBe('docker_test.status_done');
  });

  it('start click wins over an older idle state response still in flight', async () => {
    win.dtStart();
    await flush(win, 20);

    const b = badge(win);
    expect(b.className).toContain('dt-status-running');
    expect(b.spinnerVisible).toBe(true);
    expect(b.startDisabled).toBe(true);
  });

  it('start SSE transport error restores server RUNNING state instead of showing FAILED', async () => {
    await flush(win, 20);
    win.__state = {
      jobId: 'job-still-running',
      status: 'running',
      output: ['Build continues on the server'],
      lineCount: 1,
    };

    win.dtStart();
    const startSource = win.__emitters.find((e) => e.url === '/api/docker-test/start');
    expect(startSource).toBeTruthy();
    startSource.fail();
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 300));
    await flush(win, 20);

    const b = badge(win);
    expect(b.className).toContain('dt-status-running');
    expect(b.className).not.toContain('dt-status-failed');
    expect(b.outputText).toContain('Build continues on the server');
    expect(b.startDisabled).toBe(true);
    expect(win.__attachPolls).toBeGreaterThan(0);
  });

  it('navigate-back after a FAILED build → badge shows FAILED with the error output', async () => {
    win.__state = {
      jobId: 'job-fail',
      status: 'failed',
      output: ['Error: build failed', 'See logs above'],
      lineCount: 2,
    };
    loadScripts(win);
    await flush(win, 30);

    const b = badge(win);
    expect(b.className).toContain('dt-status-failed');
    expect(b.spinnerVisible).toBe(false);
    expect(b.outputText).toContain('Error: build failed');
    expect(b.text).toBe('docker_test.status_failed');
  });

  it('attach SSE delivers the __DONE__ marker → badge transitions running → done', async () => {
    win.__state = {
      jobId: 'job-running',
      status: 'running',
      output: ['Pulling fs layer 1/3'],
      lineCount: 1,
    };
    win.__attachQueue.push({
      lines: ['Pulling fs layer 2/3', 'Pulling fs layer 3/3'],
      final: '__DONE__',
    });

    loadScripts(win);
    await flush(win, 30);

    const b = badge(win);
    expect(b.className).toContain('dt-status-done');
    expect(b.spinnerVisible).toBe(false);
    expect(b.outputText).toContain('Pulling fs layer 1/3');
    expect(b.outputText).toContain('Pulling fs layer 2/3');
    expect(b.outputText).toContain('Pulling fs layer 3/3');
  });

  it('rebuild overlay is hidden after attach SSE delivers __DONE__ (start SSE reconnect)', async () => {
    win.sessionStorage.setItem('dt_session', JSON.stringify({
      status: 'idle',
      output: '',
      editor: {
        selectedPath: 'demo-gitlab/docker-compose.yml',
        content: 'services:\n  test:\n    image: alpine',
      },
    }));
    win.__state = {
      jobId: null,
      status: 'idle',
      output: [],
      lineCount: 0,
    };

    loadScripts(win);
    await flush(win, 20);

    win.dtSaveAndRebuild();
    await flush(win, 20);

    const overlay = win.document.getElementById('dtEditorOverlay');
    expect(overlay.style.display).not.toBe('none');

    win.__state = {
      jobId: 'job-rebuild',
      status: 'running',
      output: ['Step 1/3 : FROM node:20'],
      lineCount: 1,
    };
    win.__attachQueue.push({
      lines: ['Step 2/3 : COPY . .', 'Step 3/3 : CMD ["node","server.js"]'],
      final: '__DONE__',
    });

    const startSource = win.__emitters.find((e) => e.url === '/api/docker-test/start');
    expect(startSource).toBeTruthy();
    startSource.fail();

    await new Promise((resolvePromise) => setTimeout(resolvePromise, 350));
    await flush(win, 30);

    expect(overlay.style.display).toBe('none');

    const b = badge(win);
    expect(b.className).toContain('dt-status-done');
    expect(b.spinnerVisible).toBe(false);
    expect(b.outputText).toContain('Step 1/3 : FROM node:20');
    expect(b.outputText).toContain('Step 2/3 : COPY . .');
    expect(b.outputText).toContain('Step 3/3 : CMD ["node","server.js"]');
    expect(win.__attachPolls).toBeGreaterThan(0);
  });
});
