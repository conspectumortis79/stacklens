import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const SOURCE = resolve(here, '..', '..', '..', 'src', 'main', 'resources', 'static', 'js', 'fragments', 'ticket-refinement.js');

const source = readFileSync(SOURCE, 'utf8');

function sliceBetween(startMarker, endMarker) {
  const from = source.indexOf(startMarker);
  const to = source.indexOf(endMarker, from);
  return source.slice(from, to === -1 ? from + 1400 : to);
}

describe('Ticket-Aufbereitung: subtask refresh must never request id "null"', () => {
  const handler = sliceBetween("var prepId = btn.getAttribute", ".finally(function(){");
  const loader = sliceBetween('function fetchAndRender(', 'function renderRepoAnalyses(');

  it('reads the preparation id from the button', () => {
    expect(handler).toContain("btn.getAttribute('data-prep-id')");
  });

  it('keeps the user-facing hint when no preparation id is available', () => {
    expect(handler).toContain('subtasks_no_prep_id');
  });

  it('refreshes the record it just posted to instead of the reset running id', () => {
    expect(handler).toContain('fetchAndRender(prepId)');
    expect(handler).not.toContain('fetchAndRender(window._sp.runningId)');
  });

  it('guards the detail loader against empty and literal null ids', () => {
    expect(loader).toMatch(/id === null \|\| id === undefined \|\| id === '' \|\| id === 'null'/);
  });
});
