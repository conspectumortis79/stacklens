import { describe, it, expect } from 'vitest';

const GITLAB = 'http://localhost:3080';
const MR_IID = 1;
const DIFFS_URL = `${GITLAB}/demo-group/backend-api/-/merge_requests/${MR_IID}/diffs`;
const FILE = 'src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt';
const FILE_KEY = FILE.replace(/[/.]/g, '-');
const LINE = 42;

async function fetchHtml(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
  return res.text();
}

describe('REAL GitLab: file-block anchor id exists on the diff page', () => {
  it('the file-block uses id="file-<file_path_with_/_and_._as_->>"', async () => {
    const html = await fetchHtml(DIFFS_URL);
    const expectedId = `file-${FILE_KEY}`;
    const re = new RegExp(`id="${expectedId}"`);
    expect(
      re.test(html),
      `Expected id="${expectedId}" on the live GitLab diff page`,
    ).toBe(true);
  });

  it('REGRESSION: bare /diffs has NO matching element — clicking lands on top', async () => {
    const html = await fetchHtml(DIFFS_URL);
    const ids = new Set();
    const re = /id="([^"]+)"/g;
    let m;
    while ((m = re.exec(html)) !== null) ids.add(m[1]);
    expect(ids.has('diffs')).toBe(false);
    expect(ids.has('top')).toBe(false);
  });

  it('FIX: a /diffs#file-<file_key> URL scrolls to the file block', async () => {
    const html = await fetchHtml(`${DIFFS_URL}#file-${FILE_KEY}`);
    const re = new RegExp(`id="file-${FILE_KEY}"`);
    expect(re.test(html)).toBe(true);
  });

  it('FIX: a /diffs#L<file_key>_<line> URL scrolls to the specific line', async () => {
    const html = await fetchHtml(`${DIFFS_URL}#L${FILE_KEY}_${LINE}`);
    const re = new RegExp(`id="L${FILE_KEY}_${LINE}"`);
    expect(re.test(html)).toBe(true);
  });
});