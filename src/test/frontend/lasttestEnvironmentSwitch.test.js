import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFragmentScripts, extractFunction, makeWindow, readJs, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const LT = resolve(RES, 'templates/fragments/lasttest-analysis');
const HELPERS = resolve(LT, 'scripts-helpers.html');

const ENVS = [
  { name: 'Fake Atlassian Demo', host: 'localhost', port: '8081', scheme: 'http' },
  { name: 'Fake GitHub Demo', host: 'localhost', port: '8082', scheme: 'http' },
];

function bootWindow(environments, selected) {
  const win = makeWindow('<div id="ltK6Content"></div>');
  runInWindow(win, extractFunction(readJs(resolve(RES, 'static/js/app.js')), 'escHtml'));
  runInWindow(win, [
    'var _ltDetectedEnvironments = [];',
    'var _ltSelectedEnvName = null;',
    'var _ltK6Script = null;',
    'window.ltShowK6Calls = [];',
    'window.ltShowK6 = function (script) {',
    '  window.ltShowK6Calls.push(script);',
    '  document.getElementById("ltK6Content").innerHTML = ltEnvSelectorHtml();',
    '};',
  ].join('\n'));
  runInWindow(win, extractFragmentScripts(HELPERS));
  win._ltDetectedEnvironments = environments;
  win._ltSelectedEnvName = selected;
  win._ltK6Script = 'export default function () {}';
  win.document.getElementById('ltK6Content').innerHTML = win.ltEnvSelectorHtml();
  return win;
}

function chips(win) {
  return [...win.document.querySelectorAll('#ltK6Content button')];
}

function chipLabels(buttons) {
  return buttons.filter((b) => b.classList.contains('btn-success')).map((b) => b.textContent);
}

describe('Umgebungs-Chips der Lasttest-Analyse', () => {
  let win;

  beforeEach(() => {
    win = bootWindow(ENVS, ENVS[0].name);
  });

  it('liefert einen vollständigen onclick-Handler pro Chip', () => {
    const buttons = chips(win);

    expect(buttons.length).toBe(2);
    expect(buttons[0].getAttribute('onclick')).toBe('ltSelectEnvironment("Fake Atlassian Demo")');
    expect(buttons[1].getAttribute('onclick')).toBe('ltSelectEnvironment("Fake GitHub Demo")');
    expect([...buttons[0].attributes].map((a) => a.name)).toEqual(['onclick', 'class', 'style']);
    buttons.forEach((b) => expect(() => new win.Function(b.getAttribute('onclick'))).not.toThrow());
  });

  it('switched die Umgebung beim Klick auf einen anderen Chip', () => {
    chips(win)[1].click();

    expect(win._ltSelectedEnvName).toBe('Fake GitHub Demo');
    expect(win.ltShowK6Calls).toEqual(['export default function () {}']);
    expect(chipLabels(chips(win))).toEqual(['Fake GitHub Demohttp://localhost:8082']);
  });

  it('hält den aktiven Chip bei erneutem Klick auf denselben Chip', () => {
    chips(win)[0].click();

    expect(win._ltSelectedEnvName).toBe('Fake Atlassian Demo');
    expect(chipLabels(chips(win))).toEqual(['Fake Atlassian Demohttp://localhost:8081']);
  });

  it('kapselt Sonderzeichen im Umgebungsnamen in den Handler', () => {
    const tricky = 'Atlassian "Demo" <b>& \'x\' \\ \n ende';
    const other = bootWindow([{ name: tricky, host: 'host.docker.internal', port: '11434', scheme: 'http' }], 'Fake GitHub Demo');

    other.document.querySelector('#ltK6Content button').click();

    expect(other._ltSelectedEnvName).toBe(tricky);
    expect(other.document.querySelector('#ltK6Content button').querySelector('b')).toBeNull();
  });
});
