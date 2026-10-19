import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { installBrowserStubs, resetDom, readSource, loadScript } from './_load.js';

installBrowserStubs();

function loadCodeQaScripts() {
  loadScript(window, 'js/app.js');
  loadScript(window, 'js/mr-violations.js');
  loadScript(window, 'js/fragments/code-qa.js');
}

describe('code-qa link rendering uses delegated data-attribute wiring', () => {
  beforeEach(() => {
    resetDom();
    window.i18n = { t: (k) => k };
    document.body.innerHTML = '<div id="mrResultCard"></div>';
    loadCodeQaScripts();
  });

  afterEach(() => {
    delete window.i18n;
    delete window.mrOpenUrl;
  });

  it('registers the delegated handler only once across repeated page script loads', () => {
    loadScript(window, 'js/fragments/code-qa.js');
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML = '<a id="again" data-mr-open-url="https://example/one">x</a>';

    document.getElementById('again').click();

    expect(window.mrOpenUrl).toHaveBeenCalledTimes(1);
  });

  it('never embeds a url inside an inline onclick handler', () => {
    const sources = ['js/fragments/code-qa.js', 'js/mr-link-builder.js', 'js/shared-helpers.js'];

    sources.forEach((file) => {
      expect(readSource(file)).not.toMatch(/onclick=["']mrOpenUrl\(/);
    });
  });

  it('opens the url carried by data-mr-open-url on click', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="mrSugStatus_0"><a href="#" data-mr-open-url="https://gitlab.example/g/r/-/merge_requests/7">ok</a></div>';

    document.querySelector('#mrSugStatus_0 a').click();

    expect(window.mrOpenUrl).toHaveBeenCalledTimes(1);
    expect(window.mrOpenUrl).toHaveBeenCalledWith('https://gitlab.example/g/r/-/merge_requests/7');
  });

  it('keeps a quote character inside the url inert', () => {
    window.mrOpenUrl = vi.fn();
    const hostile = "https://example/a' onclick='alert(1)";
    document.body.innerHTML =
      '<div id="mrSugStatus_1"><a href="#" data-mr-open-url="' + hostile.replace(/"/g, '&quot;') + '">x</a></div>';

    document.querySelector('#mrSugStatus_1 a').click();

    expect(window.mrOpenUrl).toHaveBeenCalledTimes(1);
    expect(window.mrOpenUrl.mock.calls[0][0]).toBe(hostile);
  });

  it('ignores clicks outside the open-url region', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML = '<button id="other">x</button>';

    document.getElementById('other').click();

    expect(window.mrOpenUrl).not.toHaveBeenCalled();
  });
});
