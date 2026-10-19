import { afterEach, describe, expect, it, vi } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFragmentScripts, makeWindow, readJs, runInWindow } from './support/fragmentLoader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPT_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/ticket-refinement/scripts.html',
);
const SCRIPT = extractFragmentScripts(SCRIPT_FRAGMENT);
const PREPARATION_ID = 42;
const TICKET_KEY = 'PROJ-1';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function preparationWith(suggestedSubtasks) {
  return {
    id: PREPARATION_ID,
    status: 'COMPLETED',
    reviewsJson: JSON.stringify([
      {
        ticketKey: TICKET_KEY,
        originalSummary: 'Refine ticket',
        originalDescription: '',
        improvedDescription: '',
        state: 'OK',
        openPoints: [],
        repoAnalyses: [],
        suggestedSubtasks,
      },
    ]),
  };
}

async function waitUntil(assertion) {
  await vi.waitFor(assertion, { timeout: 1000, interval: 10 });
}

async function boot(preparation) {
  const STATIC = resolve(__dirname, '../../main/resources/static/js');
  const win = makeWindow(
    '<form id="spForm">' +
      '<input id="spParentKey">' +
      '<input id="spSingleOnly" type="checkbox" checked>' +
      '<select id="spJiraConfig"></select>' +
      '<textarea id="spTemplate"></textarea>' +
      '<button id="spStart" type="button"></button>' +
      '<button id="spCancel" type="button"></button>' +
      '<button id="spShowDefault" type="button"></button>' +
    '</form>' +
    '<div id="spDatabaseMultiSelect"></div>' +
    '<div id="spNoDbHint"></div>' +
    '<div id="spRepoList"><div id="spNoRepoHint"></div></div>' +
    '<div id="spProgressCard"><div id="spProgressStep"></div></div>' +
    '<div id="spResultsCard"><div id="spStatus"></div><div id="spResults"></div></div>' +
    '<div id="hxCard-ticket-refinement" class="hx-card"></div>',
  );
  const subtaskRequests = [];
  const detailRequests = [];

  win._sp = { gen: 0, runningId: null, lastOpenId: PREPARATION_ID };
  win.fetch = vi.fn(async (url, options = {}) => {
    const path = String(url);
    const method = String(options.method || 'GET').toUpperCase();

    if (method === 'POST' && path === `/api/ticket-refinement/${PREPARATION_ID}/subtasks/${TICKET_KEY}`) {
      const body = JSON.parse(options.body);
      subtaskRequests.push(body);
      return {
        ok: true,
        status: 200,
        json: async () => ({ subtasks: body.subtasks.map((subtask) => ({ summary: subtask.summary })) }),
      };
    }
    if (method === 'GET' && path === '/api/ticket-refinement') {
      return {
        ok: true,
        status: 200,
        json: async () => ([{ id: PREPARATION_ID, status: 'COMPLETED', parentTicketKey: TICKET_KEY }]),
      };
    }
    if (method === 'GET' && path === `/api/ticket-refinement/${PREPARATION_ID}`) {
      detailRequests.push(path);
      return { ok: true, status: 200, json: async () => clone(preparation) };
    }
    return { ok: true, status: 200, json: async () => [] };
  });

  runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));
  runInWindow(win, readJs(resolve(STATIC, 'shared-helpers.js')));
  runInWindow(win, readJs(resolve(STATIC, 'history-list.js')));
  runInWindow(win, SCRIPT);
  await waitUntil(() => {
    expect(win.document.querySelectorAll('.sp-subtask-card').length).toBe(
      JSON.parse(preparation.reviewsJson)[0].suggestedSubtasks.length,
    );
  });

  return { win, subtaskRequests, detailRequests };
}

describe('Ticket-Refinement subtask removal', () => {
  const windows = [];

  afterEach(() => {
    windows.splice(0).forEach((win) => win.close());
  });

  it('removes the selected suggestion via X and submits only the remaining edited draft', async () => {
    const { win, subtaskRequests, detailRequests } = await boot(preparationWith([
      { summary: 'Keep me', description: 'Original keep description', priority: 'Minor' },
      { summary: 'Remove me', description: 'Do not create this', priority: 'Minor' },
    ]));
    windows.push(win);

    const summaries = win.document.querySelectorAll('.sp-subtask-summary');
    const descriptions = win.document.querySelectorAll('.sp-subtask-description');
    summaries[0].value = 'Keep me edited';
    descriptions[0].value = 'Edited keep description';
    win.document.querySelectorAll('.sp-subtask-remove')[1].click();

    expect(win.document.querySelectorAll('.sp-subtask-card')).toHaveLength(1);
    expect(win.document.querySelector('.sp-subtasks-head').textContent).toContain('(1)');
    expect(win.document.querySelector('.sp-subtask-summary').value).toBe('Keep me edited');
    expect(win.document.querySelector('.sp-subtask-description').value).toBe('Edited keep description');
    expect(JSON.parse(win._sp.lastDetail.reviewsJson)[0].suggestedSubtasks).toEqual([
      expect.objectContaining({ summary: 'Keep me edited', description: 'Edited keep description' }),
    ]);

    const detailCallsBeforeCreate = detailRequests.length;
    win.document.querySelector('[data-act="create-subtasks"]').click();
    await waitUntil(() => expect(subtaskRequests).toHaveLength(1));
    await waitUntil(() => expect(detailRequests.length).toBeGreaterThan(detailCallsBeforeCreate));

    expect(subtaskRequests[0].subtasks).toHaveLength(1);
    expect(subtaskRequests[0].subtasks[0]).toEqual(
      expect.objectContaining({ summary: 'Keep me edited', description: 'Edited keep description' }),
    );
  });

  it('removes the complete subtask section when the last suggestion is dismissed', async () => {
    const { win } = await boot(preparationWith([
      { summary: 'Only suggestion', description: 'Optional work', priority: 'Minor' },
    ]));
    windows.push(win);

    win.document.querySelector('.sp-subtask-remove').click();

    expect(win.document.querySelector('.sp-subtasks')).toBeNull();
    expect(win.document.querySelector('[data-act="create-subtasks"]')).toBeNull();
  });

  it('ignores an invalid suggestion index without removing another card', async () => {
    const { win } = await boot(preparationWith([
      { summary: 'First', description: '', priority: 'Minor' },
      { summary: 'Second', description: '', priority: 'Minor' },
    ]));
    windows.push(win);

    const removeButton = win.document.querySelector('.sp-subtask-remove');
    removeButton.setAttribute('data-subtask-index', '99');
    removeButton.click();

    expect(win.document.querySelectorAll('.sp-subtask-card')).toHaveLength(2);
  });
});
