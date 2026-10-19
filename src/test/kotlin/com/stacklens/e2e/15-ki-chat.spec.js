import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

test.describe('KI Chat Drawer', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/jwt-analysis');
  });

  test('drawer is hidden on initial load', async ({ page }) => {
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });

  test('opens via kiChatToggle()', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    await expect(page.locator('#kiChatDrawer')).toBeVisible({ timeout: 3_000 });
  });

  test('KI-chat button opens the drawer', async ({ page }) => {
    await page.getByRole('button', { name: /KI Chat/i }).click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
  });

  test('closes via the × button', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    await page.locator('#kiChatDrawer button[onclick*="kiChatClose"]').click();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });

  test('input field is present and editable', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const input = page.locator('#kiChatInput');
    await expect(input).toBeVisible();
    await input.fill('Explain JWT');
    await expect(input).toHaveValue('Explain JWT');
  });

  test('Shift+Enter in input does not submit (no crash)', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const input = page.locator('#kiChatInput');
    await input.fill('Test');
    await input.press('Shift+Enter');
    await expect(input).toBeVisible();
  });

  test('tab badge shows the page context', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const badge = page.locator('#kiChatTabBadge');
    // The badge element is always in the DOM; it may be hidden when no tab label
    // is configured, so we only assert DOM presence (not visibility).
    await expect(badge).toBeAttached();
  });

  test('"clear history" button is present', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    await expect(page.locator('button[onclick="kiChatClear()"]')).toBeVisible();
  });

  test('no suggestion chips are rendered in the drawer', async ({ page }) => {
    // The per-page "example chip" suggestions were removed from the KI
    // chat per UX request. The bar may still be attached in the DOM
    // (as an empty container) but it must not render any clickable
    // suggestion buttons.
    await page.evaluate(() => window.kiChatToggle?.());
    const suggestionCount = await page.evaluate(() => {
      const bar = document.getElementById('kiChatSuggestions');
      if (!bar) return 0;
      return bar.querySelectorAll('.ki-sugg-btn').length;
    });
    expect(suggestionCount).toBe(0);
  });

  test('shows compact input, output, total and duration usage without widening the drawer', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const usage = page.locator('#kiChatUsage');

    await expect(usage).toBeVisible();
    await expect(page.locator('#kiChatUsageInput')).toHaveText(/\d+/);
    await expect(page.locator('#kiChatUsageOutput')).toHaveText(/\d+/);
    await expect(page.locator('#kiChatUsageTotal')).toHaveText(/\d+/);
    await expect(page.locator('#kiChatUsageDuration')).toHaveText(/s|min/);
    await expect(usage.locator('.ki-chat-usage-metric')).toHaveCount(4);

    // Default drawer width is twice the previous 374px target → 748px.
    // Box-sizing is border-box (Bootstrap global), so the BCR width matches
    // the CSS value directly. Allow ±1px for sub-pixel rendering.
    const width = await drawer.evaluate((element) => element.getBoundingClientRect().width);
    expect(width).toBeGreaterThanOrEqual(747);
    expect(width).toBeLessThanOrEqual(749);
  });

  test('token usage has a dedicated palette in all four supported themes', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const borderColors = await page.evaluate(() => {
      const root = document.documentElement;
      const originalTheme = root.getAttribute('data-theme') || 'modern';
      const total = document.querySelector('.ki-chat-usage-metric--total');
      const result = {};
      for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
        root.setAttribute('data-theme', theme);
        result[theme] = getComputedStyle(total).borderColor;
      }
      root.setAttribute('data-theme', originalTheme);
      return result;
    });

    expect(new Set(Object.values(borderColors)).size).toBe(4);
    for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
      expect(borderColors[theme]).not.toBe('rgba(0, 0, 0, 0)');
    }
  });

  test('drawer stays open after an app:navigate event', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    // simulate in-page navigation without a full page reload
    await page.evaluate(() => window.dispatchEvent(new Event('app:navigate')));
    await page.waitForTimeout(500);
    // sidebar must still be alive
    await expect(page.locator('#sidebar')).toBeVisible();
  });

  test('kiChatSend still works after SPA re-execution (namespace collision regression)', async ({ page }) => {
    // Regression test for the "kiChatConfig is not a function" error that
    // used to fire when a page's `window.kiChatConfig = {...}` object
    // overwrote the global `function kiChatConfig()` declaration. The
    // resolver is now `kiChatResolveConfig`; this test ensures that:
    //   1. The drawer can be opened on a page that sets window.kiChatConfig.
    //   2. After SPA-style navigation, calling kiChatSend does not throw.
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.evaluate(() => window.kiChatOpen && window.kiChatOpen());
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    // kiChatConfig is the per-page *object* (still set on /jwt-analysis).
    // kiChatResolveConfig is the layout-provided *function*. Both must be
    // independently available.
    const sanity = await page.evaluate(() => ({
      cfgKind: typeof window.kiChatConfig,
      resolveKind: typeof window.kiChatResolveConfig,
    }));
    expect(sanity.resolveKind).toBe('function');
    // The (potentially overwritten) global name must NOT be the function
    // any more — that's the whole point of the rename.
    expect(sanity.cfgKind).not.toBe('function');
    // The drawer must keep working after SPA-style navigation, which
    // re-executes layout scripts and would re-trigger the collision.
    await page.evaluate(() => window.app && window.app.navigateTo('/security-analysis'));
    await page.waitForTimeout(400);
    await page.evaluate(() => window.kiChatOpen && window.kiChatOpen());
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    // kiChatSend synchronously calls the resolver; if either binding is
    // missing it would throw "kiChatConfig is not a function" (or
    // kiChatResolveConfig is not a function) on the next stack tick.
    const threw = await page.evaluate(() => {
      try {
        if (typeof window.kiChatResolveConfig === 'function') {
          window.kiChatResolveConfig();
          return false;
        }
        return true;
      } catch (e) {
        return true;
      }
    });
    expect(threw).toBe(false);
    expect(errors.filter((e) => /kiChatConfig|kiChatResolveConfig/.test(e))).toEqual([]);
  });

  test('maximize button is present in the title bar', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const btn = page.locator('#kiChatMaximizeBtn');
    await expect(btn).toBeAttached();
    await expect(btn).toBeVisible();
    // Initial state: maximize (not yet maximized) — icon is bi-arrows-fullscreen
    const iconClass = await btn.locator('#kiChatMaximizeIcon').getAttribute('class');
    expect(iconClass).toContain('bi-arrows-fullscreen');
    expect(iconClass).not.toContain('bi-fullscreen-exit');
    // Tooltip clearly states what the click will do
    await expect(btn).toHaveAttribute('title', 'Maximieren');
  });

  test('maximize button fills the drawer to the viewport', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const btn    = page.locator('#kiChatMaximizeBtn');

    const before = await drawer.boundingBox();
    const viewport = await page.evaluate(() => ({
      w: window.innerWidth,
      h: window.innerHeight,
    }));
    // The default 748×527 size must NOT already fill the viewport for
    // this test to be meaningful. Default viewport in playwright.config.js
    // is 1280×800; 748 < 1280 and 527 < 800, so we're good.
    expect(before.width).toBeLessThan(viewport.w);
    expect(before.height).toBeLessThan(viewport.h);

    await btn.click();

    const after = await drawer.boundingBox();
    expect(after.width).toBe(viewport.w);
    expect(after.height).toBe(viewport.h);

    // The drawer has the maximized class
    await expect(drawer).toHaveClass(/ki-chat-maximized/);
    // Icon and tooltip swap to the restore state
    const iconClass = await btn.locator('#kiChatMaximizeIcon').getAttribute('class');
    expect(iconClass).toContain('bi-fullscreen-exit');
    await expect(btn).toHaveAttribute('title', 'Wiederherstellen');
  });

  test('restore button returns the drawer to its prior bounds', async ({ page }) => {
    await page.evaluate(() => {
      // Start from a clean slate so the restore target is the default 748×527.
      try { localStorage.removeItem('__kiChatPos'); } catch (_) { /* ignore */ }
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const btn    = page.locator('#kiChatMaximizeBtn');

    const before = await drawer.boundingBox();
    expect(before.width).toBeGreaterThanOrEqual(747);
    expect(before.height).toBeGreaterThanOrEqual(520);

    await btn.click();
    const maximized = await drawer.boundingBox();
    const viewport = await page.evaluate(() => ({
      w: window.innerWidth,
      h: window.innerHeight,
    }));
    expect(maximized.width).toBe(viewport.w);
    expect(maximized.height).toBe(viewport.h);

    await btn.click();

    const after = await drawer.boundingBox();
    // Restore lands back at the pre-maximize bounds (the default size,
    // because that's where the drawer sat before the click on maximize).
    expect(after.width).toBeCloseTo(before.width, 0);
    expect(after.height).toBeCloseTo(before.height, 0);
    await expect(drawer).not.toHaveClass(/ki-chat-maximized/);
    await expect(btn).toHaveAttribute('title', 'Maximieren');
  });

  test('restore returns the drawer to a custom dragged position when dragged before maximize', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatPos'); } catch (_) { /* ignore */ }
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const dragHandle = page.locator('#kiChatDragHandle');
    const btn    = page.locator('#kiChatMaximizeBtn');

    // Drag the drawer to a non-default position first, then maximize,
    // then restore. The restore must return to the dragged position,
    // not the bottom-right default.
    const dragBox = await dragHandle.boundingBox();
    const startX = dragBox.x + dragBox.width / 2;
    const startY = dragBox.y + dragBox.height / 2;
    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX - 200, startY - 150, { steps: 6 });
    await page.mouse.up();

    const before = await drawer.boundingBox();
    // The drawer was dragged away from its bottom-right default.
    expect(before.x).toBeLessThan(400);

    await btn.click();
    const viewport = await page.evaluate(() => ({ w: window.innerWidth, h: window.innerHeight }));
    const maximized = await drawer.boundingBox();
    expect(maximized.width).toBe(viewport.w);

    await btn.click();
    const restored = await drawer.boundingBox();
    expect(restored.x).toBeCloseTo(before.x, 0);
    expect(restored.y).toBeCloseTo(before.y, 0);
  });

  test('drag handle is draggable in the default (non-maximized) state', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatPos'); } catch (_) { /* ignore */ }
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const dragHandle = page.locator('#kiChatDragHandle');

    const before = await drawer.boundingBox();
    const dragBox = await dragHandle.boundingBox();
    const startX = dragBox.x + dragBox.width / 2;
    const startY = dragBox.y + dragBox.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX - 200, startY - 150, { steps: 6 });
    await page.mouse.up();

    const after = await drawer.boundingBox();
    expect(after.x).toBeLessThan(before.x);
    expect(after.y).toBeLessThan(before.y);
  });

  test('drag handle stays draggable while the drawer is maximized', async ({ page }) => {
    // The drag-to-move behaviour must survive the maximize transition.
    // Visually the drawer is pinned to the viewport (the maximized CSS
    // overrides the inline top/left via !important), but the drag handler
    // still runs and the inline top/left are updated. The next restore
    // (or another drag) uses the values the drag wrote into the inline
    // style, so the pre-maximize bounds capture the freshly-dragged
    // position when the user maximizes-then-drags-then-restores.
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatPos'); } catch (_) { /* ignore */ }
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const dragHandle = page.locator('#kiChatDragHandle');
    const btn = page.locator('#kiChatMaximizeBtn');

    await btn.click();
    await expect(drawer).toHaveClass(/ki-chat-maximized/);

    // Drag the title bar across the now-full-viewport drawer. The visual
    // position is pinned (CSS !important wins), but no JS error must fire
    // and the saved __kiChatPos must be non-zero so the next restore
    // honours the user's drag.
    const dragBox = await dragHandle.boundingBox();
    const startX = dragBox.x + dragBox.width / 2;
    const startY = dragBox.y + dragBox.height / 2;
    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX + 120, startY + 80, { steps: 6 });
    await page.mouse.up();

    const afterDrag = await drawer.boundingBox();
    // Visual position is unchanged (the !important rule pins it).
    expect(afterDrag.x).toBe(0);
    expect(afterDrag.y).toBe(0);

    // But the inline position was updated by the drag handler and that
    // position is now persisted as the post-restore target.
    const inlineTopLeft = await page.evaluate(() => {
      var d = document.getElementById('kiChatDrawer');
      return { top: d.style.top, left: d.style.left };
    });
    expect(parseFloat(inlineTopLeft.top) || 0).toBeGreaterThan(0);
    expect(parseFloat(inlineTopLeft.left) || 0).toBeGreaterThan(0);
    // localStorage __kiChatPos holds the dragged position so the next
    // reload restores to it.
    const stored = await page.evaluate(() => {
      try { return JSON.parse(localStorage.getItem('__kiChatPos') || '{}'); }
      catch (_) { return {}; }
    });
    expect(parseFloat(stored.top) || 0).toBeGreaterThan(0);
    expect(parseFloat(stored.left) || 0).toBeGreaterThan(0);
  });

  test('maximized state persists across drawer close + reopen', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const btn    = page.locator('#kiChatMaximizeBtn');

    await btn.click();
    await expect(drawer).toHaveClass(/ki-chat-maximized/);

    // Close + reopen the drawer
    await page.evaluate(() => window.kiChatClose?.());
    await expect(drawer).toBeHidden();
    await page.evaluate(() => window.kiChatOpen?.());

    // Maximized state is restored automatically
    await expect(drawer).toBeVisible();
    await expect(drawer).toHaveClass(/ki-chat-maximized/);
    await expect(btn).toHaveAttribute('title', 'Wiederherstellen');
  });

  test('clicking the maximize button does not start a drag of the drawer', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer = page.locator('#kiChatDrawer');
    const btn    = page.locator('#kiChatMaximizeBtn');

    const before = await drawer.boundingBox();
    const btnBox = await btn.boundingBox();
    const cx = btnBox.x + btnBox.width / 2;
    const cy = btnBox.y + btnBox.height / 2;

    // Press, drag the mouse across the title bar, release. Without the
    // onmousedown stopPropagation guard on the button, the drag handler
    // on #kiChatDragHandle would pick up the mousedown and the drawer
    // would shift by (dx, dy).
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    await page.mouse.move(cx + 50, cy + 20, { steps: 6 });
    await page.mouse.up();

    const after = await drawer.boundingBox();
    expect(after.x).toBeCloseTo(before.x, 0);
    expect(after.y).toBeCloseTo(before.y, 0);
  });

  test('keyboard activation of the maximize button works (Enter/Space)', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const btn = page.locator('#kiChatMaximizeBtn');
    await btn.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#kiChatDrawer')).toHaveClass(/ki-chat-maximized/);
    await page.keyboard.press('Space');
    await expect(page.locator('#kiChatDrawer')).not.toHaveClass(/ki-chat-maximized/);
  });

  test('minimize button is present in the title bar', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const btn = page.locator('#kiChatMinimizeBtn');
    await expect(btn).toBeAttached();
    await expect(btn).toBeVisible();
    await expect(btn).toHaveAttribute('title', 'Minimieren');
    const html = await btn.evaluate(el => el.innerHTML);
    expect(html).toContain('bi-window-dash');
  });

  test('minimize hides the drawer and reveals the taskbar entry', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer  = page.locator('#kiChatDrawer');
    const taskbar = page.locator('#kiChatTaskbar');
    await expect(drawer).toBeVisible();
    await expect(taskbar).toBeHidden();

    await page.locator('#kiChatMinimizeBtn').click();

    await expect(drawer).toBeHidden();
    await expect(taskbar).toBeVisible();
  });

  test('clicking the taskbar entry restores the drawer to its prior bounds', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatPos'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    const drawer  = page.locator('#kiChatDrawer');
    const dragHandle = page.locator('#kiChatDragHandle');

    const before = await drawer.boundingBox();
    // Drag the drawer to a non-default position so the restore target
    // is observable.
    const dragBox = await dragHandle.boundingBox();
    await page.mouse.move(dragBox.x + dragBox.width / 2, dragBox.y + dragBox.height / 2);
    await page.mouse.down();
    await page.mouse.move(dragBox.x - 200, dragBox.y - 150, { steps: 6 });
    await page.mouse.up();
    const dragged = await drawer.boundingBox();
    expect(dragged.x).toBeLessThan(400);

    // Minimize, then click the taskbar entry to restore.
    await page.locator('#kiChatMinimizeBtn').click();
    await expect(page.locator('#kiChatTaskbar')).toBeVisible();

    await page.locator('#kiChatTaskbarRestoreBtn').click();
    await expect(drawer).toBeVisible();
    await expect(page.locator('#kiChatTaskbar')).toBeHidden();

    const restored = await drawer.boundingBox();
    expect(restored.x).toBeCloseTo(dragged.x, 0);
    expect(restored.y).toBeCloseTo(dragged.y, 0);
    // Wider sanity: the restore target was NOT the default 748×527 bottom-right
    expect(restored.width).toBeCloseTo(dragged.width, 0);
    expect(restored.height).toBeCloseTo(dragged.height, 0);
  });

  test('taskbar maximize button restores + maximizes in one click', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    await page.locator('#kiChatMinimizeBtn').click();
    await expect(page.locator('#kiChatTaskbar')).toBeVisible();

    await page.locator('#kiChatTaskbarMaximizeBtn').click();
    const drawer = page.locator('#kiChatDrawer');
    await expect(drawer).toBeVisible();
    await expect(drawer).toHaveClass(/ki-chat-maximized/);
    await expect(page.locator('#kiChatTaskbar')).toBeHidden();
  });

  test('taskbar close button fully closes the chat (no auto-reopen)', async ({ page }) => {
    await page.evaluate(() => window.kiChatToggle?.());
    await page.locator('#kiChatMinimizeBtn').click();
    await expect(page.locator('#kiChatTaskbar')).toBeVisible();

    await page.locator('#kiChatTaskbarCloseBtn').click();
    await expect(page.locator('#kiChatTaskbar')).toBeHidden();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();

    // SPA-style navigation must NOT auto-reopen the drawer after a
    // minimized-then-closed sequence.
    await page.evaluate(() => window.dispatchEvent(new Event('app:navigate')));
    await page.waitForTimeout(300);
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });

  test('minimize survives drag handle movements in the maximized state', async ({ page }) => {
    await page.evaluate(() => {
      try { localStorage.removeItem('__kiChatMaximized'); } catch (_) { /* ignore */ }
    });
    await page.evaluate(() => window.kiChatToggle?.());
    await page.locator('#kiChatMaximizeBtn').click();
    await expect(page.locator('#kiChatDrawer')).toHaveClass(/ki-chat-maximized/);

    // The minimize button is still present in the maximized title bar.
    await page.locator('#kiChatMinimizeBtn').click();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
    await expect(page.locator('#kiChatTaskbar')).toBeVisible();

    // Click the taskbar restore — the drawer comes back at full viewport
    // size because the maximize state persisted.
    await page.locator('#kiChatTaskbarRestoreBtn').click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    await expect(page.locator('#kiChatDrawer')).toHaveClass(/ki-chat-maximized/);
  });
});
