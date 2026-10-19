import { describe, it, expect } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFragmentScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS = resolve(__dirname, '../../main/resources/templates/fragments/tooling/scripts.html');

const DOM = `
  <div id="newToolCard"></div>
  <div id="newToolFormSection"></div>
  <div id="newToolProgressSection" style="display:none"></div>
  <div id="newToolErrorSection" style="display:none"></div>
  <p id="errorMessage"></p>
  <div id="progressStepText" data-i18n="tooling.generating_start">Wird gestartet…</div>
  <div id="progressBar" style="width:15%"></div>
  <div class="tl-step" id="gStep1" style="display:none"><span class="tl-step-icon pending">1</span><span>Code wird eingelesen</span></div>
  <div class="tl-step" id="gStep2"><span class="tl-step-icon pending">2</span><span>KI generiert Kotlin-Code</span></div>
  <div class="tl-step" id="gStep3"><span class="tl-step-icon pending">3</span><span>Code wird zu JAR kompiliert</span></div>
  <div id="toolListContent"></div>
  <input id="inputToolName" value="Tetris" />
  <textarea id="inputScriptContent">baue ein tetris</textarea>
  <div id="tlFormAlertArea"></div>
  <button id="btnGenerate">generieren</button>
`;

function json(body) {
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
}

function statusOf(status, currentStep) {
  return { data: { id: 7, status, currentStep: currentStep === undefined ? null : currentStep, errorMessage: null } };
}

function boot(queuedStatuses) {
  const win = makeWindow(DOM);
  const timers = [];
  const intervals = [];
  const alerts = [];
  const errors = [];
  let statusCursor = 0;

  win.setTimeout = (fn, delay) => {
    timers.push({ fn, delay, cancelled: false });
    return timers.length;
  };
  win.clearTimeout = (id) => {
    if (timers[id - 1]) timers[id - 1].cancelled = true;
  };
  win.setInterval = (fn, delay) => {
    intervals.push({ fn, delay, cancelled: false });
    return intervals.length;
  };
  win.clearInterval = (id) => {
    if (intervals[id - 1]) intervals[id - 1].cancelled = true;
  };
  win.showAlert = (message, type) => { alerts.push({ message, type }); };
  win.tlFetchStatus = () => {
    const next = queuedStatuses[Math.min(statusCursor, queuedStatuses.length - 1)];
    statusCursor++;
    return Promise.resolve(next);
  };
  win.fetch = (url, init) => {
    if (init && init.method === 'POST') return json({ id: 7, status: 'PENDING' });
    return json([]);
  };

  runInWindow(win, extractFragmentScripts(SCRIPTS));

  const api = {
    win,
    alerts,
    timers,
    intervals,
    barWidth: () => win.document.getElementById('progressBar').style.width,
    stepText: () => win.document.getElementById('progressStepText').textContent,
    stepClasses: (n) => win.document.getElementById('gStep' + n).className,
    progressVisible: () => win.document.getElementById('newToolProgressSection').style.display,
    errorVisible: () => win.document.getElementById('newToolErrorSection').style.display,
    errorText: () => win.document.getElementById('errorMessage').textContent,
    runNextPoll: async () => {
      const pending = timers.filter((t) => !t.cancelled);
      const timer = pending[pending.length - 1];
      if (!timer) throw new Error('kein Poll eingeplant');
      timer.fn();
      await flush();
      await flush();
    },
  };
  return api;
}

function flush() {
  return new Promise((r) => setTimeout(r, 0));
}

async function startGeneration(api, name) {
  api.win.document.getElementById('inputToolName').value = name;
  api.win.document.getElementById('inputScriptContent').value = 'baue ein tetris';
  api.win.startGeneration();
  await flush();
  await flush();
}

describe('Tooling-Fortschritt: Der Balken hängt nicht fest', () => {
  it('holt den Status sofort nach dem Start und danach in 3-Sekunden-Takten', async () => {
    const api = boot([statusOf('ANALYZING', 'Tool wird vorbereitet …')]);
    await startGeneration(api, 'Tetris');

    expect(api.timers[0].delay).toBe(0);
    expect(api.timers.length).toBe(1);
    await api.runNextPoll();
    expect(api.timers[api.timers.length - 1].delay).toBe(3000);
  });

  it('setzt Balken und Schritte bei einem weiteren Generierungslauf komplett zurück', async () => {
    const api = boot([
      statusOf('BUILDING', 'Code wird kompiliert …'),
      statusOf('READY'),
      statusOf('ANALYZING', 'Tool wird vorbereitet …'),
    ]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();
    expect(api.barWidth()).toBe('80%');
    await api.runNextPoll();

    expect(api.progressVisible()).toBe('none');
    expect(api.alerts.map((a) => a.message)).toContain('Tool erfolgreich generiert und kompiliert!');

    await startGeneration(api, 'Tetris');
    expect(api.barWidth()).toBe('15%');
    expect(api.stepClasses(2)).toBe('tl-step');
    expect(api.stepText()).toContain('Wird gestartet');

    await api.runNextPoll();
    expect(api.barWidth()).toBe('25%');
  });

  it('wandert mit jedem Status nach vorne und bleibt nicht auf einer Position stehen', async () => {
    const api = boot([
      statusOf('ANALYZING', 'Tool wird vorbereitet …'),
      statusOf('GENERATING', 'KI generiert Kotlin-Code …'),
      statusOf('BUILDING', 'Code wird kompiliert …'),
    ]);

    await startGeneration(api, 'Tetris');
    const seen = [];
    for (let i = 0; i < 3; i++) {
      await api.runNextPoll();
      seen.push(api.barWidth());
    }

    expect(seen).toEqual(['25%', '55%', '80%']);
  });

  it('beendet das Polling bei CANCELLED und zeigt die Abbruch-Meldung', async () => {
    const api = boot([
      statusOf('GENERATING', 'KI generiert Kotlin-Code …'),
      { data: { id: 7, status: 'CANCELLED', currentStep: null, errorMessage: 'Tool-Generierung wurde abgebrochen. Starte die Generierung neu.' } },
    ]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();
    expect(api.barWidth()).toBe('55%');

    const pollsBefore = api.timers.length;
    await api.runNextPoll();

    expect(api.win._tlGen.active).toBe(false);
    expect(api.timers.length).toBe(pollsBefore);
    expect(api.timers[api.timers.length - 1].cancelled).toBe(true);
    expect(api.progressVisible()).toBe('none');
    expect(api.errorVisible()).toBe('block');
    expect(api.errorText()).toBe('Tool-Generierung wurde abgebrochen. Starte die Generierung neu.');
  });

  it('zeigt die Standard-Abbruchsmeldung, wenn der Server keine liefert', async () => {
    const api = boot([{ data: { id: 7, status: 'CANCELLED', currentStep: null, errorMessage: null } }]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();

    expect(api.errorText()).toBe('Generierung abgebrochen. Starte die Generierung neu.');
  });

  it('beendet das Polling, wenn das Tool verschwunden ist (404)', async () => {
    const api = boot([
      statusOf('GENERATING', 'KI generiert Kotlin-Code …'),
      { gone: true },
    ]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();

    const pollsBefore = api.timers.length;
    await api.runNextPoll();

    expect(api.win._tlGen.active).toBe(false);
    expect(api.timers.length).toBe(pollsBefore);
    expect(api.progressVisible()).toBe('none');
    expect(api.win.document.getElementById('newToolFormSection').style.display).toBe('block');
    expect(api.alerts.map((a) => a.message)).toContain('Tool wurde entfernt oder ist nicht mehr vorhanden.');
  });

  it('pollt bei einem Netzwerkfehler weiter', async () => {
    const api = boot([
      { retry: true },
      statusOf('BUILDING', 'Code wird kompiliert …'),
    ]);

    await startGeneration(api, 'Tetris');
    const pollsBefore = api.timers.length;
    await api.runNextPoll();
    expect(api.timers.length).toBe(pollsBefore + 1);
    expect(api.win._tlGen.active).toBe(true);

    await api.runNextPoll();
    expect(api.barWidth()).toBe('80%');
  });

  it('stoppt den Ticker und das Polling zusammen mit dem Abschluss', async () => {
    const api = boot([
      statusOf('GENERATING', 'KI generiert Kotlin-Code …'),
      statusOf('READY'),
    ]);

    await startGeneration(api, 'Tetris');
    expect(api.intervals.length).toBe(1);
    expect(api.intervals[0].delay).toBe(1000);

    await api.runNextPoll();
    await api.runNextPoll();

    expect(api.intervals[0].cancelled).toBe(true);
    expect(api.win._tlGen.ticker).toBe(null);
    expect(api.win._tlGen.poll).toBe(null);
  });

  it('zeigt im Schritttext, wie lange die Generierung schon läuft', async () => {
    const api = boot([statusOf('GENERATING', 'KI generiert Kotlin-Code …')]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();

    expect(api.stepText()).toContain('KI generiert Kotlin-Code …');
    expect(api.stepText()).toMatch(/läuft seit \d+:\d{2}/);
  });

  it('übernimmt keinen älteren Schritttext über einen neueren Stand', async () => {
    const api = boot([
      statusOf('BUILDING', 'Code wird kompiliert …'),
      statusOf('GENERATING', 'KI generiert Kotlin-Code …'),
    ]);

    await startGeneration(api, 'Tetris');
    await api.runNextPoll();
    await api.runNextPoll();

    expect(api.stepText()).toContain('Code wird kompiliert …');
    expect(api.barWidth()).toBe('80%');
  });

  it('behandelt einen unbekannten Status als Fortsetzungsfall statt als Ende', async () => {
    const api = boot([{ data: { id: 7, status: 'PENDING', currentStep: 'Tool wird vorbereitet …', errorMessage: null } }]);

    await startGeneration(api, 'Tetris');
    const pollsBefore = api.timers.length;
    await api.runNextPoll();

    expect(api.timers.length).toBe(pollsBefore + 1);
    expect(api.win._tlGen.active).toBe(true);
    expect(api.barWidth()).toBe('15%');
    expect(api.stepText()).toContain('Tool wird vorbereitet …');
  });
});
