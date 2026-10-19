import { describe, it, expect } from 'vitest';


function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderSuggestedSubtasks(r, data, t = (k, fb) => fb) {
  var subs = Array.isArray(r.suggestedSubtasks) ? r.suggestedSubtasks : [];
  if (subs.length === 0) return '';
  var createdList = lookupCreatedSubtasks(data, r.ticketKey);
  var createdSet = {};
  createdList.forEach(function(s){ createdSet[s] = true; });

  var html =
    '<div class="sp-subtasks">' +
    '  <div class="sp-subtasks-head">' +
    '    <i class="bi bi-diagram-3-fill"></i>' +
    '    <span>' + escapeHtml(t('ticket_refinement.subtasks_title','Subtasks')) + ' (' + subs.length + ')</span>' +
    '  </div>' +
    '  <div class="sp-subtasks-list">';
  subs.forEach(function(st, idx){
    var sum = st.summary || '';
    var desc = st.description || '';
    var type = st.issueType || 'Subtask';
    var prio = st.priority || 'Minor';
    var labels = Array.isArray(st.labels) ? st.labels : [];
    var existingKey = st.existingKey || '';
    var isCreated = !!createdSet[sum];
    var cardCls = 'sp-subtask-card' + (existingKey ? ' sp-subtask-card--existing' : '') +
                  (isCreated ? ' sp-subtask-card--created' : '');
    html +=
      '<div class="' + cardCls + '" data-subtask-index="' + idx + '"' +
      (existingKey ? ' data-existing-key="' + escapeHtml(existingKey) + '"' : '') + '>' +
      '  <div class="sp-subtask-head">' +
      '    <i class="bi bi-diagram-3"></i>' +
      '    <span class="sp-subtask-type">' + escapeHtml(type) + '</span>' +
      (existingKey ? '<span class="sp-subtask-existing-badge">Refine ' + escapeHtml(existingKey) + '</span>' : '') +
      (isCreated ? '<span class="sp-subtask-created-badge">Created</span>' : '') +
      '    <span class="sp-subtask-priority">' + escapeHtml(prio) + '</span>' +
      '    <button type="button" class="sp-subtask-remove" data-subtask-index="' + idx + '">×</button>' +
      '  </div>' +
      '  <textarea class="sp-subtask-summary" data-subtask-index="' + idx + '" rows="1">' + escapeHtml(sum) + '</textarea>' +
      '  <textarea class="sp-subtask-description" data-subtask-index="' + idx + '" rows="4">' + escapeHtml(desc) + '</textarea>' +
      '  <div class="sp-subtask-labels" data-subtask-index="' + idx + '">' +
      (labels.length ? labels.map(function(l){ return '<span class="sp-subtask-label">' + escapeHtml(l) + '</span>'; }).join('') : '') +
      '  </div>' +
      '</div>';
  });
  html +=
    '  </div>' +
    '  <div class="sp-subtask-actions">' +
    '    <button type="button" class="sp-btn sp-btn-primary" data-act="create-subtasks" data-key="' + escapeHtml(r.ticketKey) + '"' +
    (r.state === 'FAILED' ? ' disabled' : '') + '>' +
    '      Create subtasks' +
    '    </button>' +
    '  </div>' +
    '</div>';
  return html;
}

function lookupCreatedSubtasks(data, ticketKey) {
  var raw = data && data.subtasksCreatedJson;
  if (!raw) return [];
  try {
    var map = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    var list = map && map[ticketKey];
    return Array.isArray(list) ? list : [];
  } catch (_) { return []; }
}

function collectSubtaskDrafts(ticketCard) {
  var drafts = [];
  if (!ticketCard) return drafts;
  var cards = ticketCard.querySelectorAll('.sp-subtask-card');
  Array.prototype.forEach.call(cards, function(card){
    var idx = card.getAttribute('data-subtask-index');
    var sumEl = card.querySelector('.sp-subtask-summary');
    var descEl = card.querySelector('.sp-subtask-description');
    var labelsEl = card.querySelector('.sp-subtask-labels');
    var sum = sumEl ? sumEl.value.trim() : '';
    if (!sum) return;
    var desc = descEl ? descEl.value : '';
    var labels = [];
    if (labelsEl) {
      Array.prototype.forEach.call(labelsEl.querySelectorAll('.sp-subtask-label'), function(lbl){
        var txt = lbl.textContent.trim();
        if (txt) labels.push(txt);
      });
    }
    var type = card.querySelector('.sp-subtask-type');
    var prio = card.querySelector('.sp-subtask-priority');
    drafts.push({
      summary: sum,
      description: desc || '',
      issueType: type ? (type.textContent.trim() || 'Subtask') : 'Subtask',
      priority: prio ? (prio.textContent.trim() || 'Minor') : 'Minor',
      labels: labels,
    });
  });
  return drafts;
}

function makeCardDom(subs) {
  var root = { querySelectorAll: function(){ return subs.map(function(s, idx){
    return {
      getAttribute: function(k){ return k === 'data-subtask-index' ? String(idx) : null; },
      querySelector: function(sel){
        if (sel === '.sp-subtask-summary') return { value: s.summary ?? '' };
        if (sel === '.sp-subtask-description') return { value: s.description ?? '' };
        if (sel === '.sp-subtask-type') return { textContent: s.issueType ?? 'Subtask' };
        if (sel === '.sp-subtask-priority') return { textContent: s.priority ?? 'Minor' };
        if (sel === '.sp-subtask-labels') return {
          querySelectorAll: function(){ return (s.labels ?? []).map(function(l){ return { textContent: l }; }); }
        };
        return null;
      },
    };
  }); } };
  return root;
}


describe('Ticket-Refinement subtasks rendering', () => {
  it('returns empty string when there are no suggestions', () => {
    const html = renderSuggestedSubtasks({ suggestedSubtasks: [] }, {});
    expect(html).toBe('');
  });

  it('returns empty string when suggestedSubtasks field is missing', () => {
    const html = renderSuggestedSubtasks({}, {});
    expect(html).toBe('');
  });

  it('renders one editable card per suggestion', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'OK',
      suggestedSubtasks: [
        { summary: 'Sub A', description: 'Body A', priority: 'Major', labels: ['backend'] },
        { summary: 'Sub B', description: 'Body B', priority: 'Minor', labels: [] },
      ],
    };
    const html = renderSuggestedSubtasks(r, {});
    expect(html).toContain('sp-subtasks-head');
    expect(html).toContain('Subtasks (2)');
    expect(html.match(/sp-subtask-card/g).length).toBe(2);
    expect(html).toContain('Sub A');
    expect(html).toContain('Body A');
    expect(html).toContain('Major');
    expect(html).toContain('backend');
    expect(html).toContain('Sub B');
    expect(html).toContain('Minor');
  });

  it('renders existing-key refinement badge for refinements of an existing subtask', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'OK',
      suggestedSubtasks: [
        { summary: 'Refine existing', existingKey: 'PROJ-99' },
      ],
    };
    const html = renderSuggestedSubtasks(r, {});
    expect(html).toContain('sp-subtask-card--existing');
    expect(html).toContain('Refine PROJ-99');
    expect(html).toContain('data-existing-key="PROJ-99"');
  });

  it('renders Created badge when subtask is in the created ledger', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'OK',
      suggestedSubtasks: [
        { summary: 'Sub A' },
        { summary: 'Sub B' },
      ],
    };
    const data = { subtasksCreatedJson: JSON.stringify({ 'PROJ-1': ['Sub A'] }) };
    const html = renderSuggestedSubtasks(r, data);
    expect(html).toContain('sp-subtask-card--created');
    expect(html).toContain('>Created<');
    expect(html.match(/Created/g).length).toBeGreaterThanOrEqual(1);
  });

  it('disables the create button when the review state is FAILED', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'FAILED',
      suggestedSubtasks: [{ summary: 'Sub' }],
    };
    const html = renderSuggestedSubtasks(r, {});
    expect(html).toContain('data-act="create-subtasks"');
    expect(html).toContain('disabled');
  });

  it('enables the create button for OK / NEEDS_REVIEW states', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'NEEDS_REVIEW',
      suggestedSubtasks: [{ summary: 'Sub' }],
    };
    const html = renderSuggestedSubtasks(r, {});
    expect(html).not.toContain('disabled');
  });

  it('escapes HTML in subtask summary, description and labels', () => {
    const r = {
      ticketKey: 'PROJ-1',
      state: 'OK',
      suggestedSubtasks: [
        {
          summary: '<script>alert(1)</script>',
          description: 'Body with <b>HTML</b>',
          labels: ['<danger>'],
        },
      ],
    };
    const html = renderSuggestedSubtasks(r, {});
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;');
    expect(html).toContain('&lt;b&gt;HTML&lt;/b&gt;');
    expect(html).toContain('&lt;danger&gt;');
  });
});

describe('Ticket-Refinement lookupCreatedSubtasks', () => {
  it('returns empty list when no ledger is present', () => {
    expect(lookupCreatedSubtasks({}, 'PROJ-1')).toEqual([]);
    expect(lookupCreatedSubtasks({ subtasksCreatedJson: '' }, 'PROJ-1')).toEqual([]);
  });

  it('returns the array stored under the given ticket key', () => {
    expect(lookupCreatedSubtasks(
      { subtasksCreatedJson: JSON.stringify({ 'PROJ-1': ['A', 'B'], 'PROJ-2': ['C'] }) },
      'PROJ-1'
    )).toEqual(['A', 'B']);
  });

  it('returns empty array when the key is absent from the ledger', () => {
    expect(lookupCreatedSubtasks(
      { subtasksCreatedJson: JSON.stringify({ 'PROJ-2': ['C'] }) },
      'PROJ-1'
    )).toEqual([]);
  });

  it('survives malformed JSON without throwing', () => {
    expect(lookupCreatedSubtasks(
      { subtasksCreatedJson: 'this is not json' },
      'PROJ-1'
    )).toEqual([]);
  });
});

describe('Ticket-Refinement collectSubtaskDrafts', () => {
  it('returns empty list when no cards exist', () => {
    const card = { querySelectorAll: function(){ return []; } };
    expect(collectSubtaskDrafts(card)).toEqual([]);
  });

  it('returns empty list when called with null', () => {
    expect(collectSubtaskDrafts(null)).toEqual([]);
  });

  it('collects summary, description and labels from the rendered cards', () => {
    const dom = makeCardDom([
      { summary: 'Sub A', description: 'Body A', issueType: 'Subtask', priority: 'Minor', labels: ['backend'] },
      { summary: 'Sub B', description: 'Body B', issueType: 'Subtask', priority: 'Major', labels: ['api', 'urgent'] },
    ]);
    const drafts = collectSubtaskDrafts(dom);
    expect(drafts).toHaveLength(2);
    expect(drafts[0].summary).toBe('Sub A');
    expect(drafts[0].description).toBe('Body A');
    expect(drafts[0].labels).toEqual(['backend']);
    expect(drafts[1].summary).toBe('Sub B');
    expect(drafts[1].labels).toEqual(['api', 'urgent']);
  });

  it('skips subtasks whose summary was cleared by the user', () => {
    const dom = makeCardDom([
      { summary: '  ', description: 'skip me' },
      { summary: 'Keep me', description: 'ok' },
    ]);
    const drafts = collectSubtaskDrafts(dom);
    expect(drafts).toHaveLength(1);
    expect(drafts[0].summary).toBe('Keep me');
  });

  it('falls back to Subtask / Minor defaults when type / priority text is empty', () => {
    const dom = makeCardDom([
      { summary: 'S', description: 'd', issueType: '', priority: '' },
    ]);
    const drafts = collectSubtaskDrafts(dom);
    expect(drafts[0].issueType).toBe('Subtask');
    expect(drafts[0].priority).toBe('Minor');
  });
});