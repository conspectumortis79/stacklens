import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

describe('SQL-Analyse saCheckTestBtn (button + done-pill + hint consistency)', () => {
  let _allInserts;
  let _analyzeOk;
  let _dataOk;
  let saCheckTestBtn;

  function setupDom() {
    document.body.innerHTML =
      '<button id="btnRunTest" disabled></button>' +
      '<div id="analyzeDone" class="sa-status-pill"></div>' +
      '<div id="analyzeNeedDataHint" class="sa-card" style="display:none;"></div>';
  }

  beforeEach(() => {
    _allInserts = '';
    _analyzeOk = false;
    _dataOk = false;

    setupDom();

    saCheckTestBtn = function () {
      const btn = document.getElementById('btnRunTest');
      const hasInserts = !!(_allInserts && _allInserts.trim().length > 0);
      const ready = _analyzeOk && _dataOk && hasInserts;
      if (btn) btn.disabled = !ready;
      const donePill = document.getElementById('analyzeDone');
      if (donePill) donePill.classList.toggle('visible', ready);
      const hint = document.getElementById('analyzeNeedDataHint');
      if (hint) {
        hint.style.display = ready ? 'none' : '';
      }
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });


  it('initial state: button disabled, done pill hidden, hint shown', () => {
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
    expect(document.getElementById('analyzeNeedDataHint').style.display).not.toBe('none');
  });

  it('analyze ok + data ok but empty inserts → button disabled (the bug)', () => {
    _analyzeOk = true;
    _dataOk = true;
    _allInserts = '';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
    expect(document.getElementById('analyzeNeedDataHint').style.display).not.toBe('none');
  });

  it('analyze ok + data ok + whitespace-only inserts → button disabled', () => {
    _analyzeOk = true;
    _dataOk = true;
    _allInserts = '   \n\t  \n  ';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
  });

  it('analyze ok + data ok + real inserts → button enabled, done pill shown, hint hidden', () => {
    _analyzeOk = true;
    _dataOk = true;
    _allInserts = 'INSERT INTO users VALUES (1, \'Alice\');';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(false);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(true);
    expect(document.getElementById('analyzeNeedDataHint').style.display).toBe('none');
  });

  it('analyze ok only (no data yet) → button disabled, hint shown', () => {
    _analyzeOk = true;
    _dataOk = false;
    _allInserts = '';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
    expect(document.getElementById('analyzeNeedDataHint').style.display).not.toBe('none');
  });

  it('data ok only (no analyze yet) → button disabled, hint shown', () => {
    _analyzeOk = false;
    _dataOk = true;
    _allInserts = 'INSERT INTO users VALUES (1);';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
    expect(document.getElementById('analyzeNeedDataHint').style.display).not.toBe('none');
  });

  it('state reset (after re-analyze) → button re-disabled, hint re-shown', () => {
    _analyzeOk = true;
    _dataOk = true;
    _allInserts = 'INSERT INTO users VALUES (1);';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(false);

    _analyzeOk = false;
    _dataOk = false;
    _allInserts = '';
    saCheckTestBtn();
    expect(document.getElementById('btnRunTest').disabled).toBe(true);
    expect(document.getElementById('analyzeDone').classList.contains('visible')).toBe(false);
    expect(document.getElementById('analyzeNeedDataHint').style.display).not.toBe('none');
  });

  it('handles missing DOM elements gracefully (e.g. when not on the SQL tab)', () => {
    document.body.innerHTML = '';
    expect(() => saCheckTestBtn()).not.toThrow();
  });
});
