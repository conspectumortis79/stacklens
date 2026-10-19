import { describe, it, expect } from 'vitest';


function buildLinks(violation, mrUrl) {
  const isGithub = mrUrl.indexOf('github.com') > -1;
  const diffsUrl = mrUrl
    ? mrUrl.replace(/\/$/, '') + (isGithub ? '/files' : '/diffs')
    : '';

  let fileBlockAnchor = '';
  let fileKey = '';
  if (!isGithub && diffsUrl && violation.file) {
    fileKey = (violation.file || '')
      .replace(/`/g, '')
      .replace(/^\.\//, '')
      .replace(/[/.]/g, '-');
    fileBlockAnchor = '#file-' + fileKey;
  }

  let lineAnchor = '';
  if (diffsUrl && violation.lineNumber > 0) {
    if (isGithub) {
      if (
        violation.lineRange &&
        violation.lineRange.end &&
        violation.lineRange.end !== violation.lineRange.start
      ) {
        lineAnchor = '#L' + violation.lineNumber + '-L' + violation.lineRange.end;
      } else {
        lineAnchor = '#L' + violation.lineNumber;
      }
    } else {
      if (
        violation.lineRange &&
        violation.lineRange.end &&
        violation.lineRange.end !== violation.lineRange.start
      ) {
        lineAnchor =
          '#L' + fileKey + '_' + violation.lineNumber + '-' + violation.lineRange.end;
      } else {
        lineAnchor = '#L' + fileKey + '_' + violation.lineNumber;
      }
    }
  }

  const fileLinkUrl = diffsUrl ? diffsUrl + fileBlockAnchor : '';

  const lineBadgeIsLink = !!fileLinkUrl;

  const openInMrUrl = diffsUrl ? diffsUrl + lineAnchor : '';

  return { isGithub, diffsUrl, lineAnchor, fileLinkUrl, lineBadgeIsLink, openInMrUrl };
}

describe('code-qa suggestion URL builder', () => {
  it('builds a file-block anchor for GitLab file link + line anchor for line link', () => {
    const r = buildLinks(
      { file: 'src/Foo.php', lineNumber: 42, lineRange: null },
      'https://gitlab.example.com/g/p/-/merge_requests/7',
    );
    expect(r.fileLinkUrl).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-Foo-php',
    );
    expect(r.lineAnchor).toBe('#Lsrc-Foo-php_42');
    expect(r.openInMrUrl).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#Lsrc-Foo-php_42',
    );
  });

  it('builds a #L42-L50 anchor (double-L) for GitHub line ranges', () => {
    const r = buildLinks(
      { file: 'src/A.php', lineNumber: 7, lineRange: { start: 7, end: 9 } },
      'https://github.com/o/r/pull/5',
    );
    expect(r.lineAnchor).toBe('#L7-L9');
    expect(r.fileLinkUrl).toBe('https://github.com/o/r/pull/5/files');
  });

  it('builds a #L<file>_<N>-<M> anchor for GitLab line ranges', () => {
    const r = buildLinks(
      { file: 'src/Foo.php', lineNumber: 7, lineRange: { start: 7, end: 9 } },
      'https://gitlab.example.com/g/p/-/merge_requests/7',
    );
    expect(r.lineAnchor).toBe('#Lsrc-Foo-php_7-9');
    expect(r.fileLinkUrl).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-Foo-php',
    );
  });

  it('omits the line anchor when lineNumber is 0 (no line known)', () => {
    const r = buildLinks(
      { file: 'src/Foo.php', lineNumber: 0, lineRange: null },
      'https://gitlab.example.com/g/p/-/merge_requests/7',
    );
    expect(r.lineAnchor).toBe('');
    expect(r.fileLinkUrl).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-Foo-php',
    );
  });

  it('treats a lineRange where start === end as a single line (no range anchor)', () => {
    const r = buildLinks(
      { file: 'src/Foo.php', lineNumber: 42, lineRange: { start: 42, end: 42 } },
      'https://gitlab.example.com/g/p/-/merge_requests/7',
    );
    expect(r.lineAnchor).toBe('#Lsrc-Foo-php_42');
    expect(r.fileLinkUrl).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-Foo-php',
    );
  });

  it('returns empty URLs when mrUrl is missing', () => {
    const r = buildLinks({ lineNumber: 42, lineRange: null }, '');
    expect(r.diffsUrl).toBe('');
    expect(r.fileLinkUrl).toBe('');
    expect(r.lineBadgeIsLink).toBe(false);
    expect(r.openInMrUrl).toBe('');
  });
});