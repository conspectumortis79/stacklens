import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';
import { createDemoRepoFixture, removeDemoRepoFixture } from './demoRepoFixture.js';
import { deleteLocalReposByPath } from './localRepoRegistry.js';
import { addRepoThroughDirectoryBrowser, removeRepoCard, selectRepoCard } from './loggingRepoUi.js';

const BASE_URL = 'http://127.0.0.1:8091';

const SAMPLE_LOG = [
  '2025-01-15 10:00:01 INFO  com.shop.checkout.CheckoutApplication : Started CheckoutApplication in 4.2 seconds',
  '2025-01-15 10:00:05 WARN  com.shop.checkout.SubscriptionRenewalWorker : Feature flag billing.requireAddressForRenewal activated 2h ago',
  '2025-01-15 10:00:06 WARN  com.shop.billing.AddressService : Cache miss for userId=99314',
  '2025-01-15 10:00:08 ERROR com.shop.checkout.CheckoutService : Failed to complete renewal order for userId=99314',
  'java.lang.NullPointerException: billingAddress must not be null',
  '    at com.shop.checkout.CheckoutService.completeOrder(CheckoutService.kt:142)',
  '    at com.shop.checkout.SubscriptionRenewalWorker.renew(SubscriptionRenewalWorker.kt:88)',
  '    at com.shop.checkout.EventConsumer.onMessage(EventConsumer.kt:51)',
  '    at jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)',
  '    at jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)',
  '2025-01-15 10:00:09 ERROR com.shop.checkout.EventConsumer : Message moved to DLQ after 3 retries',
  '2025-01-15 10:00:12 WARN  com.shop.billing.BillingDb : HikariPool-1 - Connection pool exhaustion approaching (active=48/50)',
  '2025-01-15 10:00:15 INFO  com.shop.checkout.CheckoutService : Order id=88412 completed successfully',
  '2025-01-15 10:00:18 INFO  com.shop.billing.NotificationService : Sent renewal reminder to userId=12001',
].join('\n');

const INFO_ONLY_LOG = [
  '2025-01-15 10:00:01 INFO  com.shop.checkout.CheckoutApplication : Started CheckoutApplication in 4.2 seconds',
  '2025-01-15 10:00:05 INFO  com.shop.checkout.NotificationService : Sent reminder to userId=12001',
  '2025-01-15 10:00:10 INFO  com.shop.checkout.Worker : Health check OK',
  '2025-01-15 10:00:15 INFO  com.shop.billing.Worker : Scheduled job completed',
].join('\n');

let repoPath;

test.describe('DEMO: full logging-fix flow with demo Java/Kotlin repo', () => {
  test.beforeEach(async () => {
    removeDemoRepoFixture(repoPath);
    repoPath = createDemoRepoFixture('demo-checkout-service');
    await deleteLocalReposByPath(BASE_URL, repoPath);
  });

  test.afterAll(async () => {
    const pathToDelete = repoPath;
    removeDemoRepoFixture(pathToDelete);
    await deleteLocalReposByPath(BASE_URL, pathToDelete);
  });

  test('complete demo: log in errors, select matching repo, run fix pipeline', async ({ page }) => {
    test.setTimeout(60_000);

    console.log('\n========== DEMO FLOW START ==========\n');

    await goto(page, '/logging');
    await page.waitForLoadState('networkidle');

    console.log('[1] Page loaded: /logging');
    const initial = await page.evaluate(() => ({
      title: document.title,
      hasLgInput: !!document.getElementById('lgInput'),
      hasRepoList: !!document.getElementById('lgLocalRepoList'),
      hasToolbar: !!document.querySelector('.lg-fix-toolbar'),
      hasFixButton: !!document.getElementById('lgBtnStartFix'),
    }));
    console.log('   initial state:', JSON.stringify(initial));
    expect(initial.hasLgInput).toBe(true);
    expect(initial.hasRepoList).toBe(true);
    expect(initial.hasToolbar).toBe(true);
    expect(initial.hasFixButton).toBe(true);

    console.log('\n[2] Paste sample log with errors/warnings/info');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();

    const afterAnalyze = await page.evaluate(() => {
      const badges = Array.from(document.querySelectorAll('#lgSummaryBadges .lg-summary-badge'));
      return {
        resultVisible: document.getElementById('lgResult').classList.contains('visible'),
        badges: badges.map(b => ({
          category: b.getAttribute('data-category'),
          isActive: b.classList.contains('lg-summary-badge-active'),
          text: b.textContent.trim(),
        })),
        summaryInfo: document.getElementById('lgSummaryInfo').textContent.trim().replace(/\s+/g, ' '),
        toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
        btnDisabled: document.getElementById('lgBtnStartFix').disabled,
        btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
      };
    });
    console.log('   after analyze:', JSON.stringify(afterAnalyze, null, 2));
    expect(afterAnalyze.resultVisible).toBe(true);
    expect(afterAnalyze.badges.length).toBeGreaterThan(0);
    const activeNonInfoBadges = afterAnalyze.badges.filter(b => b.isActive && b.category !== 'info').length;
    expect(activeNonInfoBadges).toBeGreaterThan(0);

    console.log('\n[3] Toolbar should be HIDDEN (no repo selected yet)');
    expect(afterAnalyze.toolbarDisplay).toBe('none');

    console.log('\n[4] Add demo Kotlin repo via real directory browser');
    console.log(`   fixture path: ${repoPath}`);
    await addRepoThroughDirectoryBrowser(page, repoPath);

    const afterRepoAdd = await page.evaluate((expectedPath) => {
      const cards = Array.from(document.querySelectorAll(
        `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
      ));
      return {
        cardCount: cards.length,
        firstCardHtml: cards[0] ? cards[0].outerHTML.substring(0, 400) : null,
      };
    }, repoPath);
    console.log('   after repo add:', JSON.stringify(afterRepoAdd, null, 2));
    expect(afterRepoAdd.cardCount).toBe(1);

    console.log('\n[5] Click card to select the repo');
    await selectRepoCard(page, repoPath);

    const afterCardClick = await page.evaluate((expectedPath) => {
      const card = document.querySelector(
        `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
      );
      const matchPill = card ? card.querySelector('.lg-local-repo-match') : null;
      return {
        cardSelected: card ? card.classList.contains('selected') : null,
        matchPill: matchPill ? matchPill.getAttribute('data-match') : null,
        matchPillText: matchPill ? matchPill.textContent.trim() : null,
        toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
        btnDisabled: document.getElementById('lgBtnStartFix').disabled,
        btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
        lgStack: window._lgStack ? JSON.stringify(window._lgStack) : null,
        mismatchWarn: document.getElementById('lgRepoMismatchWarn').style.display,
      };
    }, repoPath);
    console.log('   after card click:', JSON.stringify(afterCardClick, null, 2));
    expect(afterCardClick.cardSelected).toBe(true);
    expect(afterCardClick.matchPill).toBe('match');
    expect(afterCardClick.toolbarDisplay).toBe('');
    expect(afterCardClick.btnDisabled).toBe(false);

    console.log('\n[6] Remove button works');
    await removeRepoCard(page, repoPath);

    console.log('\n[7] Toolbar should be hidden again after removing repo');
    const afterRemove = await page.evaluate((expectedPath) => ({
      toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
      cardCount: document.querySelectorAll(
        `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
      ).length,
    }), repoPath);
    console.log('   after remove:', JSON.stringify(afterRemove));
    expect(afterRemove.toolbarDisplay).toBe('none');
    expect(afterRemove.cardCount).toBe(0);
  });

  test('DEMO: only-INFO logs do NOT enable fix button', async ({ page }) => {
    test.setTimeout(60_000);

    await goto(page, '/logging');
    await page.waitForLoadState('networkidle');

    await page.locator('#lgInput').fill(INFO_ONLY_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();

    const afterAnalyze = await page.evaluate(() => {
      const badges = Array.from(document.querySelectorAll('#lgSummaryBadges .lg-summary-badge'));
      return {
        badges: badges.map(b => ({
          category: b.getAttribute('data-category'),
          isActive: b.classList.contains('lg-summary-badge-active'),
        })),
        activeNonInfo: badges.filter(b => b.classList.contains('lg-summary-badge-active') && b.getAttribute('data-category') !== 'info').length,
      };
    });
    console.log('INFO-only analyze:', JSON.stringify(afterAnalyze, null, 2));
    expect(afterAnalyze.activeNonInfo).toBe(0);

    await addRepoThroughDirectoryBrowser(page, repoPath);
    await selectRepoCard(page, repoPath);

    const finalState = await page.evaluate(() => ({
      toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
      btnDisabled: document.getElementById('lgBtnStartFix').disabled,
      btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
    }));
    console.log('INFO-only + repo:', JSON.stringify(finalState));

    expect(finalState.toolbarDisplay).toBe('none');
    expect(finalState.btnDisabled).toBe(true);
  });
});
