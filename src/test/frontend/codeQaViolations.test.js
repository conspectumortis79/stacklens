import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { installBrowserStubs, resetDom } from './_load.js';
import { readSource } from './_load.js';

installBrowserStubs();

let parseViolations;

beforeEach(async () => {
  resetDom();
  const src = readSource('js/mr-violations.js');
  window.eval(src + '\nwindow.MrViolations = MrViolations;\n');
  parseViolations = window.MrViolations.parse;
});

afterEach(() => {
  vi.useRealTimers();
  delete window.MrViolations;
});

describe('mr-violations parser', () => {
  it('parses a single violation with File/Line/Original/Suggestion blocks', () => {
    const md = [
      '### Coding Guidelines Violations',
      '',
      '❌ No comments in PHP',
      'File: src/Foo.php',
      'Line: 42',
      'Original:',
      '// bad comment',
      'Suggestion:',
      '// fixed',
    ].join('\n');
    const v = parseViolations(md);
    expect(v).toHaveLength(1);
    expect(v[0].rule).toBe('No comments in PHP');
    expect(v[0].file).toBe('src/Foo.php');
    expect(v[0].lineNumber).toBe(42);
    expect(v[0].original).toBe('// bad comment');
    expect(v[0].suggestedCode).toBe('// fixed');
  });

  it('parses German "Datei" and "Zeile" markers', () => {
    const md = [
      '❌ Regel',
      'Datei: src/A.php',
      'Zeile: 12',
      'Original:',
      'x',
      'Vorschlag:',
      'y',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].file).toBe('src/A.php');
    expect(v[0].lineNumber).toBe(12);
    expect(v[0].suggestedCode).toBe('y');
  });

  it('strips backticks around file path', () => {
    const md = [
      '❌ Regel',
      'File: `src/A.php`',
      'Line: 5',
      'Original:',
      'x',
      'Suggestion:',
      'y',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].file).toBe('src/A.php');
  });

  it('parses a line range as start line and marks it uncertain', () => {
    const md = [
      '❌ Regel',
      'File: src/A.php',
      'Line: 42-44',
      'Original:',
      'a',
      'b',
      'Suggestion:',
      'c',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].lineNumber).toBe(42);
    expect(v[0].lineRange).toEqual({ start: 42, end: 44 });
    expect(v[0].lineUncertain).toBe(true);
  });

  it('recognises "Line: unknown" as zero with uncertain flag', () => {
    const md = [
      '❌ Regel',
      'File: src/A.php',
      'Line: unknown',
      'Original:',
      'a',
      'Suggestion:',
      'b',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].lineNumber).toBe(0);
    expect(v[0].lineUncertain).toBe(true);
  });

  it('skips a violation that has no File (cannot be posted)', () => {
    const md = [
      '❌ No comments',
      'Line: 1',
      'Original:',
      'x',
      'Suggestion:',
      'y',
      '❌ Other rule',
      'File: src/B.php',
      'Line: 9',
      'Original:',
      'a',
      'Suggestion:',
      'b',
    ].join('\n');
    const v = parseViolations(md);
    expect(v).toHaveLength(1);
    expect(v[0].file).toBe('src/B.php');
    expect(v[0].lineNumber).toBe(9);
  });

  it('parses multiple violations in the same markdown', () => {
    const md = [
      '❌ Rule A',
      'File: src/A.php',
      'Line: 1',
      'Original:',
      'a',
      'Suggestion:',
      'b',
      '',
      '❌ Rule B',
      'File: src/B.php',
      'Line: 9',
      'Original:',
      'c',
      'Suggestion:',
      'd',
    ].join('\n');
    const v = parseViolations(md);
    expect(v).toHaveLength(2);
    expect(v[0].file).toBe('src/A.php');
    expect(v[1].file).toBe('src/B.php');
  });

  it('handles empty Original block (line to delete)', () => {
    const md = [
      '❌ Remove this',
      'File: src/A.php',
      'Line: 5',
      'Original:',
      'Suggestion:',
      '',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].original).toBe('');
    expect(v[0].suggestedCode).toBe('');
  });

  it('captures multiple Original lines until Suggestion appears', () => {
    const md = [
      '❌ Rule',
      'File: src/A.php',
      'Line: 1',
      'Original:',
      'line1',
      'line2',
      'line3',
      'Suggestion:',
      'new1',
      'new2',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].original).toBe('line1\nline2\nline3');
    expect(v[0].suggestedCode).toBe('new1\nnew2');
  });

  it('does not start a new violation on a "✅" or "⚠️" line', () => {
    const md = [
      '✅ all good',
      '❌ Real problem',
      'File: src/A.php',
      'Line: 3',
      'Original:',
      'x',
      'Suggestion:',
      'y',
    ].join('\n');
    const v = parseViolations(md);
    expect(v).toHaveLength(1);
    expect(v[0].file).toBe('src/A.php');
  });

  it('parses "Lines:" (plural) as start line', () => {
    const md = [
      '❌ Rule',
      'File: src/A.php',
      'Lines: 7-9',
      'Original:',
      'a',
      'Suggestion:',
      'b',
    ].join('\n');
    const v = parseViolations(md);
    expect(v[0].lineNumber).toBe(7);
    expect(v[0].lineRange).toEqual({ start: 7, end: 9 });
  });
});
