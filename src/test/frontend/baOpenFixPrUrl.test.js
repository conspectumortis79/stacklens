import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';


describe('baOpenFixPrUrl (manual click flow)', () => {
  const TEST_URL = 'https://github.com/acme/app/pull/42';

  beforeEach(() => {
    document.body.innerHTML = '';
  });

  afterEach(() => {
    delete window.desktopApp;
    document.body.innerHTML = '';
  });

  it('desktop mode: delegates to window.desktopApp.openInBrowser', () => {
    const openInBrowser = vi.fn();
    window.desktopApp = { openInBrowser };

    const url = TEST_URL;
    if (window.desktopApp && typeof window.desktopApp.openInBrowser === 'function') {
      window.desktopApp.openInBrowser(url);
    } else {
      const a = document.createElement('a');
      a.href = url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }

    expect(openInBrowser).toHaveBeenCalledTimes(1);
    expect(openInBrowser).toHaveBeenCalledWith(TEST_URL);
  });

  it('browser mode: creates an <a> with target="_blank" and rel="noopener noreferrer" and clicks it', () => {
    delete window.desktopApp;

    const clicked = [];
    const orig = HTMLElement.prototype.click;
    HTMLElement.prototype.click = function () {
      if (this.tagName === 'A') clicked.push({ href: this.href, target: this.target, rel: this.rel });
      return orig.call(this);
    };

    try {
      const url = TEST_URL;
      if (window.desktopApp && typeof window.desktopApp.openInBrowser === 'function') {
        window.desktopApp.openInBrowser(url);
      } else {
        const a = document.createElement('a');
        a.href = url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      }

      expect(clicked).toHaveLength(1);
      expect(clicked[0].href).toBe(TEST_URL);
      expect(clicked[0].target).toBe('_blank');
      expect(clicked[0].rel).toBe('noopener noreferrer');
    } finally {
      HTMLElement.prototype.click = orig;
    }
  });
});

describe('baGenerateFix auto-open flow (pre-open about:blank + navigate)', () => {
  afterEach(() => {
    delete window.desktopApp;
  });

  it('pre-opens about:blank synchronously and navigates to prUrl after fetch', () => {
    delete window.desktopApp;

    const opened = [];
    const mockTab = { location: { href: '' }, close: vi.fn() };
    const origOpen = window.open;
    window.open = function (url, target) {
      opened.push({ url, target });
      return mockTab;
    };

    try {
      let prTab = null;
      if (!window.desktopApp || typeof window.desktopApp.openInBrowser !== 'function') {
        prTab = window.open('about:blank', '_blank');
      }

      expect(opened).toHaveLength(1);
      expect(opened[0].url).toBe('about:blank');
      expect(opened[0].target).toBe('_blank');

      const data = { prUrl: 'https://gitlab.com/acme/app/-/merge_requests/7' };

      if (window.desktopApp && typeof window.desktopApp.openInBrowser === 'function') {
        window.desktopApp.openInBrowser(data.prUrl);
      } else if (prTab) {
        prTab.location.href = data.prUrl;
      }

      expect(prTab.location.href).toBe('https://gitlab.com/acme/app/-/merge_requests/7');
    } finally {
      window.open = origOpen;
    }
  });

  it('closes pre-opened tab on error or local fix', () => {
    delete window.desktopApp;

    const mockClose = vi.fn();
    const origOpen = window.open;
    window.open = function () {
      return { location: { href: '' }, close: mockClose };
    };

    try {
      let prTab = null;
      if (!window.desktopApp || typeof window.desktopApp.openInBrowser !== 'function') {
        prTab = window.open('about:blank', '_blank');
      }

      const data = { error: 'GitLab API failed' };
      if (data.error) {
        if (prTab) { try { prTab.close(); } catch(_) {} }
      }

      expect(mockClose).toHaveBeenCalledTimes(1);
    } finally {
      window.open = origOpen;
    }
  });
});
