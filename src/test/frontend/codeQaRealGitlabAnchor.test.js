import { describe, it, expect } from 'vitest';

const GITLAB = 'http://localhost:3080';

async function fetchDiffPage(mrIid, anchor = '') {
  const url = `${GITLAB}/demo-group/backend-api/-/merge_requests/${mrIid}/diffs${anchor}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`GitLab responded ${res.status} for ${url}`);
  return res.text();
}

function extractAnchorIds(html) {
  const ids = new Set();
  const re = /id="(L[^"]+)"/g;
  let m;
  while ((m = re.exec(html)) !== null) ids.add(m[1]);
  return ids;
}

describe('REAL GitLab: MR diff page uses file-path-anchored line ids, not #L42', () => {
  it('confirms the bug: the page has NO element with id="L42"', async () => {
    const html = await fetchDiffPage(1, '');
    const ids = extractAnchorIds(html);
    console.log('Anchor ids found in page:', [...ids].slice(0, 5));
    expect(ids.has('L42')).toBe(false);
  });

  it('confirming the fix format: the page DOES have an id like L{file}_42', async () => {
    const html = await fetchDiffPage(1, '');
    const ids = extractAnchorIds(html);
    const file = 'src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt';
    const expectedFileKey = file.replace(/[/.]/g, '-');
    const expectedLineId = `L${expectedFileKey}_42`;
    console.log('Expected line id:', expectedLineId);
    expect(ids.has(expectedLineId)).toBe(true);
  });

  it('mr-link-builder line URL does NOT match any element in the page (the bug)', async () => {
    const html = await fetchDiffPage(1, '');
    const ids = extractAnchorIds(html);
    const wrongAnchor = 'L42';
    expect(ids.has(wrongAnchor)).toBe(false);
  });

  it('FIX: with the file-path-aware anchor, the URL DOES match an element', async () => {
    const file = 'src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt';
    const line = 42;
    const fileKey = file.replace(/[/.]/g, '-');
    const fixedAnchor = `L${fileKey}_${line}`;
    const html = await fetchDiffPage(1, '#' + fixedAnchor);
    const ids = extractAnchorIds(html);
    expect(ids.has(fixedAnchor)).toBe(true);
  });
});