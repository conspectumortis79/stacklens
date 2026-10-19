import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DRAWER = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/ki-chat-drawer.html',
);
const THEMES_DIR = resolve(__dirname, '../../main/resources/static/css/themes');


function readDrawer() {
  return readFileSync(DRAWER, 'utf8');
}

describe('KI-Chat drawer <style> block containment', () => {
  const drawer = readDrawer();

  function findLeaks() {
    const lines = drawer.split('\n');
    let inStyle = false;
    const leaks = [];
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (/<style[\s>]/i.test(line)) inStyle = true;
      if (/<\/style>/i.test(line)) {
        inStyle = false;
        continue;
      }
      if (inStyle) continue;
      if (/^\s*(?:\.ki-|html\[data-theme=)/.test(line) && /\{/.test(line)) {
        leaks.push({ line: i + 1, text: line.trim() });
      }
    }
    return leaks;
  }

  it('does not contain any .ki-* CSS rule outside the <style> block', () => {
    const leaks = findLeaks();
    expect(
      leaks,
      'Found CSS rules outside the <style> block — they will render ' +
        'as visible text in every page (the fragment is included via ' +
        'th:replace in layout.html). Move them back inside <style>.\n' +
        leaks.map((l) => `  line ${l.line}: ${l.text}`).join('\n'),
    ).toEqual([]);
  });

  it('has exactly one <style> open and one </style> close tag', () => {
    const opens = (drawer.match(/<style[\s>]/gi) || []).length;
    const closes = (drawer.match(/<\/style>/gi) || []).length;
    expect(
      { opens, closes },
      'Mismatch between <style> and </style> tag counts. An unbalanced ' +
        'style block is exactly what caused the previous CSS-leak bug.',
    ).toEqual({ opens: 1, closes: 1 });
  });
});

describe('KI-Chat drawer DOM structure', () => {
  const drawer = readDrawer();

  it('input footer has a stable id "kiChatInputFooter" for theme targeting', () => {
    expect(
      drawer,
      'Input footer div must carry id="kiChatInputFooter" so theme CSS ' +
        'can target it directly without depending on DOM order.',
    ).toMatch(/id="kiChatInputFooter"/);
  });

  it('input footer has the class "ki-chat-input-footer"', () => {
    expect(
      drawer,
      'Input footer div must carry class="ki-chat-input-footer" so ' +
        'shared component CSS (e.g. modern defaults) can target it.',
    ).toMatch(/class="ki-chat-input-footer"/);
  });

  it('input footer is rendered as a direct child of #kiChatDrawer', () => {
    const idxDrawer = drawer.indexOf('id="kiChatDrawer"');
    const idxFooter = drawer.indexOf('id="kiChatInputFooter"');
    expect(idxDrawer).toBeGreaterThan(-1);
    expect(idxFooter).toBeGreaterThan(idxDrawer);
  });
});

describe('KI-Chat maximize / restore button', () => {
  const drawer = readDrawer();

  it('exposes a #kiChatMaximizeBtn inside the title bar', () => {
    const idxDragHandle = drawer.indexOf('id="kiChatDragHandle"');
    const idxClose      = drawer.indexOf('kiChatClose()');
    const idxMax        = drawer.indexOf('id="kiChatMaximizeBtn"');
    expect(idxDragHandle).toBeGreaterThan(-1);
    expect(idxMax).toBeGreaterThan(idxDragHandle);
    expect(idxClose).toBeGreaterThan(idxMax);
  });

  it('#kiChatMaximizeBtn click handler is kiChatToggleMaximize()', () => {
    expect(
      drawer,
      'Maximize button must call kiChatToggleMaximize() so the global ' +
        'function pointer is the canonical entry point (matches the ' +
        'pattern set by kiChatClear / kiChatClose / kiChatToggle).',
    ).toMatch(/id="kiChatMaximizeBtn"[^>]*onclick="kiChatToggleMaximize\(\)"/);
  });

  it('starts a drag with no current icon and reaches for #kiChatMaximizeIcon', () => {
    const re = /id="kiChatMaximizeIcon"[^>]*class="bi bi-arrows-fullscreen"/;
    expect(
      drawer.match(re),
      'Maximize button must carry a Bootstrap icon (bi-arrows-fullscreen) ' +
        'that kiChatToggleMaximize() can swap to bi-fullscreen-exit when ' +
        'entering the maximized state.',
    ).not.toBeNull();
  });

  it('stops mousedown bubbling so the drag handler does not grab the click', () => {
    expect(
      drawer,
      'Maximize button must stopPropagation on mousedown, otherwise the ' +
        'drag handler on #kiChatDragHandle fires alongside the click ' +
        'and the drawer drifts when the user just clicks the button.',
    ).toMatch(/id="kiChatMaximizeBtn"[^>]*onmousedown="event\.stopPropagation\(\)"/);
  });

  it('declares a #kiChatDrawer.ki-chat-maximized CSS rule that fills the viewport', () => {
    const css = drawer.match(/#kiChatDrawer\.ki-chat-maximized\s*\{([^}]+)\}/);
    expect(css, '.ki-chat-maximized CSS rule not found').not.toBeNull();
    const body = css[1];
    expect(
      /top\s*:\s*0\s*!important/.test(body),
      'ki-chat-maximized must pin top:0 (!important) so the drawer ' +
        'overrides the inline `bottom:0` anchor and fills from the ' +
        'viewport top edge.',
    ).toBe(true);
    expect(
      /left\s*:\s*0\s*!important/.test(body),
      'ki-chat-maximized must pin left:0 (!important) for the same ' +
        'reason — overrides the inline `right:0` anchor.',
    ).toBe(true);
    expect(
      /width\s*:\s*100vw\s*!important/.test(body),
      'ki-chat-maximized must set width:100vw (!important) so the ' +
        'drawer stretches across the full viewport width.',
    ).toBe(true);
    expect(
      /height\s*:\s*100vh\s*!important/.test(body),
      'ki-chat-maximized must set height:100vh (!important) so the ' +
        'drawer stretches across the full viewport height.',
    ).toBe(true);
    expect(
      /border-radius\s*:\s*0\s*!important/.test(body),
      'ki-chat-maximized must reset border-radius to 0 so the drawer ' +
        'has square corners when it fills the viewport (matching the ' +
        'native maximize/restore behaviour of OS windows).',
    ).toBe(true);
  });
});

describe('KI-Chat minimize button', () => {
  const drawer = readDrawer();

  it('exposes a #kiChatMinimizeBtn in the title bar', () => {
    const idxDrag  = drawer.indexOf('id="kiChatDragHandle"');
    const idxClose = drawer.indexOf('id="kiChatClose"') >= 0
      ? drawer.indexOf('id="kiChatClose"')
      : drawer.indexOf('kiChatClose()');
    const idxMin   = drawer.indexOf('id="kiChatMinimizeBtn"');
    const idxMax   = drawer.indexOf('id="kiChatMaximizeBtn"');
    expect(idxDrag).toBeGreaterThan(-1);
    expect(idxMin).toBeGreaterThan(idxDrag);
    expect(idxMax).toBeGreaterThan(idxMin);
    expect(idxClose).toBeGreaterThan(idxMax);
  });

  it('#kiChatMinimizeBtn starts a minimize via kiChatMinimize()', () => {
    expect(
      drawer,
      'Minimize button must call kiChatMinimize() so the global ' +
        'function pointer is the canonical entry point (matches the ' +
        'pattern set by kiChatClear / kiChatClose / kiChatToggle).',
    ).toMatch(/id="kiChatMinimizeBtn"[^>]*onclick="kiChatMinimize\(\)"/);
  });

  it('uses bi-window-dash (Windows-minimize icon) as the visual affordance', () => {
    expect(
      drawer,
      'Minimize button must carry the Bootstrap icon "bi-window-dash" — ' +
        'it visually reads as the Windows/Linux minimize glyph (a small ' +
        'horizontal bar at the bottom of a window outline).',
    ).toMatch(/id="kiChatMinimizeBtn"[\s\S]{0,500}bi-window-dash/);
  });

  it('stops mousedown bubbling so the drag handler does not grab the click', () => {
    expect(
      drawer,
      'Minimize button must stopPropagation on mousedown, otherwise the ' +
        'drag handler on #kiChatDragHandle fires alongside the click and ' +
        'the drawer drifts when the user just clicks the button.',
    ).toMatch(/id="kiChatMinimizeBtn"[^>]*onmousedown="event\.stopPropagation\(\)"/);
  });
});

describe('KI-Chat minimized taskbar entry', () => {
  const drawer = readDrawer();

  it('exposes #kiChatTaskbar as a sibling of #kiChatDrawer', () => {
    const idxDrawer  = drawer.indexOf('id="kiChatDrawer"');
    const idxTaskbar = drawer.indexOf('id="kiChatTaskbar"');
    expect(idxDrawer).toBeGreaterThan(-1);
    expect(idxTaskbar).toBeGreaterThan(idxDrawer);
    expect(drawer).toMatch(/<div id="kiChatTaskbar"[\s\S]{0,80}style="display:none/);
  });

  it('taskbar has a click-to-restore button with the chat icon + title', () => {
    expect(
      drawer,
      'Taskbar must carry a #kiChatTaskbarRestoreBtn that contains both ' +
        'the KI-Chat icon (bi-chat-dots-fill) and a "KI Chat" title so ' +
        'clicking anywhere on the entry restores the drawer.',
    ).toMatch(/id="kiChatTaskbarRestoreBtn"[\s\S]{0,600}bi-chat-dots-fill[\s\S]{0,200}KI Chat/);
  });

  it('taskbar has a maximize button so the user can skip the restore-to-default step', () => {
    expect(
      drawer,
      'Taskbar must carry a #kiChatTaskbarMaximizeBtn with a maximize ' +
        'glyph (bi-arrows-fullscreen). Clicking it restores the drawer ' +
        'AND maximizes in one step.',
    ).toMatch(/id="kiChatTaskbarMaximizeBtn"[\s\S]{0,500}bi-arrows-fullscreen/);
  });

  it('taskbar has a close button (×) that fully closes the chat', () => {
    expect(
      drawer,
      'Taskbar must carry a #kiChatTaskbarCloseBtn with a × glyph so ' +
        'the user can close the chat from the taskbar without restoring ' +
        'the drawer first.',
    ).toMatch(/id="kiChatTaskbarCloseBtn"/);
  });
});

describe('KI-Chat textarea in flexbox layout', () => {
  const drawer = readDrawer();

  it('.ki-chat-input declares min-width: 0 to defeat the textarea intrinsic min-width', () => {
    const css = drawer.match(/\.ki-chat-input\s*\{([^}]+)\}/);
    expect(css, '.ki-chat-input CSS rule not found').not.toBeNull();
    expect(
      /min-width\s*:\s*0/.test(css[1]),
      '.ki-chat-input must declare min-width: 0. Without it, the ' +
        '<textarea> retains its intrinsic content size (default cols) ' +
        'and refuses to fill the flex container, leaving empty space ' +
        'between the textarea and the send button.',
    ).toBe(true);
  });

  it('.ki-chat-input-footer declares min-width: 0 on its flex items', () => {
    const css = drawer.match(/\.ki-chat-input-footer\s*\{([^}]+)\}/);
    expect(css, '.ki-chat-input-footer CSS rule not found').not.toBeNull();
    expect(
      /min-width\s*:\s*0/.test(css[1]),
      '.ki-chat-input-footer must declare min-width: 0 (or have a ' +
        'parent that constrains its width) so its flex children can ' +
        'size correctly.',
    ).toBe(true);
  });
});

describe('KI-Chat drawer theme override coverage', () => {
  const footerRuleRegex =
    /html\[data-theme="[^"]+"\] (?:#kiChatInputFooter|\.ki-chat-input-footer)[^{]*\{([\s\S]*?)\}/;

  for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
    it(`theme-${theme}.css overrides #kiChatInputFooter/.ki-chat-input-footer background`, () => {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      const match = source.match(footerRuleRegex);
      expect(
        match,
        `theme-${theme}.css does not declare a rule for #kiChatInputFooter / ` +
          `.ki-chat-input-footer. The input footer will keep its inline ` +
          `background:#faf8f3 and stay beige regardless of the active theme.`,
      ).not.toBeNull();
      expect(
        match[1],
        `theme-${theme}.css #kiChatInputFooter rule body does not contain 'background'.`,
      ).toContain('background');
    });

    it(`theme-${theme}.css overrides #kiChatInputFooter/.ki-chat-input-footer border-top`, () => {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      const match = source.match(footerRuleRegex);
      expect(match).not.toBeNull();
      expect(
        match[1],
        `theme-${theme}.css #kiChatInputFooter rule body does not contain 'border-top'.`,
      ).toContain('border-top');
    });
  }

  it('no theme file still uses the broken #kiChatDrawer > div:last-child selector', () => {
    for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      expect(
        source.includes('#kiChatDrawer > div:last-child'),
        `theme-${theme}.css still uses '#kiChatDrawer > div:last-child'. ` +
          `This positional selector matches whichever <div> happens to be ` +
          `last in #kiChatDrawer, not the input footer. Remove it.`,
      ).toBe(false);
    }
  });
});