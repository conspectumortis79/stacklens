import { describe, expect, it } from 'vitest';
import { staticSource } from './support/sharedBrowserHarness.js';

const REVIEW_PAGE = staticSource('review-analysis.html');
const REVIEW_PPTX = staticSource('fragments/review-analysis/scripts-pptx.html');

describe('review analysis PPTX picker delegates to the shared file browser', () => {
  it('no longer ships its own overlay', () => {
    expect(REVIEW_PAGE).not.toContain('raPptxBrowserOverlay');
    expect(REVIEW_PAGE).not.toContain('raPptxBrowserUseSelection');
  });

  it('opens the shared browser with a ppt/pptx file filter', () => {
    expect(REVIEW_PPTX).toContain('__kiChatOpenDirBrowser(');
    expect(REVIEW_PPTX).toContain("files: ['ppt', 'pptx']");
    expect(REVIEW_PPTX).toContain("titleKey: 'review_analysis.pptx_browser_title'");
  });

  it('keeps reading candidates from its own endpoint', () => {
    expect(REVIEW_PPTX).toContain('/api/review-analyse/files/browse?directoriesOnly=false');
    expect(REVIEW_PPTX).toContain('function raPptxFileEntries');
  });

  it('hands the picked server path to the upload state', () => {
    expect(REVIEW_PPTX).toContain('window._ra.pptx.serverPath = picked');
    expect(REVIEW_PPTX).toContain('renderPptxChip()');
  });

  it('keeps the drag and drop path on the same state contract', () => {
    expect(REVIEW_PPTX).toContain('window._ra.pptx.file = f');
    expect(REVIEW_PPTX).toContain("raPptxFileSelected(input)");
  });
});
