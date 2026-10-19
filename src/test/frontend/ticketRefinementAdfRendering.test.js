import { describe, it, expect } from 'vitest';


function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>')
    .replace(/"/g, '"').replace(/'/g, '&#39;');
}

function renderMarkdown(text) {
  if (!text) return '';
  return '<p class="sp-jira-p">' + escapeHtml(text) + '</p>';
}

function diffPairTexts(origText, impText) {
  return ['\u0001D\u0001' + origText + '\u0001/D\u0001',
          '\u0001D\u0001' + impText + '\u0001/D\u0001'];
}

function applyDiffMarkers(html) {
  if (!html || html.indexOf('\u0001D\u0001') < 0) return html || '';
  return html.replace(/\u0001D\u0001([\s\S]*?)\u0001\/D\u0001/g,
    '<span class="sp-diff">$1</span>');
}

function renderDescriptions(r) {
  var origRaw  = r.originalDescription || '';
  var impRaw   = r.improvedDescription || '';
  var origHtml = r.originalDescriptionHtml || renderMarkdown(origRaw);
  var impHtml  = r.improvedDescriptionHtml || renderMarkdown(impRaw);

  var canPair = !r.originalDescriptionHtml && !r.improvedDescriptionHtml;

  var origHtmlDiff = origHtml, impHtmlDiff = impHtml;
  if (canPair && origRaw && impRaw) {
    var dFull = diffPairTexts(origRaw, impRaw);
    origHtmlDiff = applyDiffMarkers(renderMarkdown(dFull[0]));
    impHtmlDiff  = applyDiffMarkers(renderMarkdown(dFull[1]));
  }

  return { origHtmlDiff, impHtmlDiff };
}

describe('Ticket-Refinement ADF 1:1 rendering', () => {
  it('preserves ADF HTML for original description (Jira v3 Cloud)', () => {
    const review = {
      originalDescription: 'Flattened plain text from ADF',
      originalDescriptionHtml: '<p class="sp-jira-p">ADF <strong>rendered</strong> HTML</p>',
      improvedDescription: 'Improved text',
      improvedDescriptionHtml: '<p class="sp-jira-p">Improved <strong>ADF</strong> HTML</p>',
    };

    const { origHtmlDiff, impHtmlDiff } = renderDescriptions(review);

    expect(origHtmlDiff).toBe(review.originalDescriptionHtml);
    expect(impHtmlDiff).toBe(review.improvedDescriptionHtml);
    expect(origHtmlDiff).not.toContain('sp-diff');
    expect(impHtmlDiff).not.toContain('sp-diff');
  });

  it('applies diff for markdown fallback (no ADF HTML)', () => {
    const review = {
      originalDescription: 'Some original text',
      improvedDescription: 'Some improved text',
      originalDescriptionHtml: null,
      improvedDescriptionHtml: null,
    };

    const { origHtmlDiff, impHtmlDiff } = renderDescriptions(review);

    expect(origHtmlDiff).toContain('sp-diff');
    expect(impHtmlDiff).toContain('sp-diff');
  });

  it('preserves ADF HTML even when only one side has it', () => {
    const review = {
      originalDescription: 'Flattened plain text',
      originalDescriptionHtml: '<p class="sp-jira-p">ADF <strong>original</strong></p>',
      improvedDescription: 'Improved markdown text',
      improvedDescriptionHtml: null,
    };

    const { origHtmlDiff, impHtmlDiff } = renderDescriptions(review);

    expect(origHtmlDiff).toBe(review.originalDescriptionHtml);
    expect(impHtmlDiff).toBe(renderMarkdown(review.improvedDescription));
    expect(origHtmlDiff).not.toContain('sp-diff');
    expect(impHtmlDiff).not.toContain('sp-diff');
  });

  it('does not destroy ADF formatting elements (headings, lists, code, tables)', () => {
    const adfHtml = '<div class="sp-jira-h2">Goal</div>' +
      '<ul class="sp-jira-ul"><li>Item 1</li><li>Item 2</li></ul>' +
      '<pre class="sp-jira-codeblock"><code>code here</code></pre>' +
      '<table class="sp-jira-table"><tbody><tr><th class="sp-jira-th">A</th></tr></tbody></table>';

    const review = {
      originalDescription: 'Goal Item 1 Item 2 code here A',
      originalDescriptionHtml: adfHtml,
      improvedDescription: 'Improved text',
      improvedDescriptionHtml: '<p class="sp-jira-p">Improved</p>',
    };

    const { origHtmlDiff } = renderDescriptions(review);

    expect(origHtmlDiff).toBe(adfHtml);
    expect(origHtmlDiff).toContain('sp-jira-h2');
    expect(origHtmlDiff).toContain('sp-jira-ul');
    expect(origHtmlDiff).toContain('sp-jira-codeblock');
    expect(origHtmlDiff).toContain('sp-jira-table');
  });
});