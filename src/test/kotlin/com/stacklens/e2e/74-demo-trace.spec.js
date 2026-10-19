import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';
import { createDemoRepoFixture, removeDemoRepoFixture } from './demoRepoFixture.js';
import { deleteLocalReposByPath } from './localRepoRegistry.js';
import { addRepoThroughDirectoryBrowser, removeRepoCard, selectRepoCard } from './loggingRepoUi.js';

const BASE_URL = 'http://127.0.0.1:8091';

const SAMPLE_LOG = `2025-01-15 10:00:01 INFO  com.shop.checkout.CheckoutApplication : Started CheckoutApplication in 4.2 seconds
2025-01-15 10:00:05 WARN  com.shop.checkout.SubscriptionRenewalWorker : Feature flag billing.requireAddressForRenewal activated 2h ago
2025-01-15 10:00:06 WARN  com.shop.billing.AddressService : Cache miss for userId=99314
2025-01-15 10:00:08 ERROR com.shop.checkout.CheckoutService : Failed to complete renewal order for userId=99314
java.lang.NullPointerException: billingAddress must not be null
    at com.shop.checkout.CheckoutService.completeOrder(CheckoutService.kt:142)
    at com.shop.checkout.SubscriptionRenewalWorker.renew(SubscriptionRenewalWorker.kt:88)
    at com.shop.checkout.EventConsumer.onMessage(EventConsumer.kt:51)
    at jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
2025-01-15 10:00:09 ERROR com.shop.checkout.EventConsumer : Message moved to DLQ after 3 retries
2025-01-15 10:00:12 WARN  com.shop.billing.BillingDb : HikariPool-1 - Connection pool exhaustion approaching (active=48/50)
2025-01-15 10:00:15 INFO  com.shop.checkout.CheckoutService : Order id=88412 completed successfully
2025-01-15 10:00:18 INFO  com.shop.billing.NotificationService : Sent renewal reminder to userId=12001`;

let repoPath;

test.beforeEach(async () => {
  removeDemoRepoFixture(repoPath);
  repoPath = createDemoRepoFixture('demo-trace-checkout-service');
  await deleteLocalReposByPath(BASE_URL, repoPath);
});

test.afterAll(async () => {
  removeDemoRepoFixture(repoPath);
  await deleteLocalReposByPath(BASE_URL, repoPath);
});

test('FULL DEMO TRACE: every step documented', async ({ page }) => {
  test.setTimeout(60_000);

  console.log('\n╔══════════════════════════════════════════════════════════════╗');
  console.log('║          DEMO: LOGGING-ANALYSE → KI-FIX MIT DEMO-REPO        ║');
  console.log('╚══════════════════════════════════════════════════════════════╝\n');

  await goto(page, '/logging');
  await page.waitForLoadState('networkidle');

  console.log('─── SCHRITT 1: PAGE-LOAD ───');
  const step1 = await page.evaluate(() => ({
    title: document.title,
    hasInput: !!document.getElementById('lgInput'),
    hasRepoBlock: !!document.getElementById('lgLocalRepoList'),
    hasFixToolbar: !!document.querySelector('.lg-fix-toolbar'),
    hasFixButton: !!document.getElementById('lgBtnStartFix'),
    btnInitiallyDisabled: document.getElementById('lgBtnStartFix').disabled,
  }));
  console.log('  Title:', step1.title);
  console.log('  Log-Eingabe:', step1.hasInput ? '✓ vorhanden' : '✗ fehlt');
  console.log('  Repo-Block:', step1.hasRepoBlock ? '✓ vorhanden' : '✗ fehlt');
  console.log('  Fix-Toolbar:', step1.hasFixToolbar ? '✓ vorhanden' : '✗ fehlt');
  console.log('  Fix-Button:', step1.hasFixButton ? '✓ vorhanden' : '✗ fehlt',
    '(initial disabled =', step1.btnInitiallyDisabled + ')');
  expect(step1.hasInput).toBe(true);
  expect(step1.hasRepoBlock).toBe(true);
  expect(step1.btnInitiallyDisabled).toBe(true);

  console.log('\n─── SCHRITT 2: LOG MIT FEHLERN EINFÜGEN ───');
  await page.locator('#lgInput').fill(SAMPLE_LOG);
  await page.locator('#lgBtnAnalyze').click();
  await expect(page.locator('#lgResult')).toBeVisible();

  const step2 = await page.evaluate(() => {
    const badges = Array.from(document.querySelectorAll('#lgSummaryBadges .lg-summary-badge'));
    return {
      totalLines: document.getElementById('lgSummaryInfo').textContent.match(/(\d+)/)?.[0],
      summaryText: document.getElementById('lgSummaryInfo').textContent.trim().replace(/\s+/g, ' '),
      badges: badges.map(b => ({
        category: b.getAttribute('data-category'),
        text: b.textContent.trim(),
        active: b.classList.contains('lg-summary-badge-active'),
      })),
      toolbarVisible: getComputedStyle(document.querySelector('.lg-fix-toolbar')).display !== 'none',
      btnDisabled: document.getElementById('lgBtnStartFix').disabled,
      btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
    };
  });
  console.log('  Zeilen gesamt / mit Problemen:', step2.summaryText);
  console.log('  Erkannte Badge-Kategorien:');
  for (const b of step2.badges) {
    console.log(`    ${b.active ? '✓' : '○'} ${b.text}`);
  }
  console.log('  Fix-Toolbar sichtbar:', step2.toolbarVisible ? '✓ ja' : '✗ NEIN (kein Repo!)');
  console.log('  Fix-Button:', step2.btnText, step2.btnDisabled ? '(disabled)' : '(enabled!)');
  expect(step2.toolbarVisible).toBe(false);
  expect(step2.btnText).toMatch(/\(\d+\)/);

  console.log('\n─── SCHRITT 3: DEMO-REPO HINZUFÜGEN ───');
  console.log('  Demo-Repo-Pfad:', repoPath);
  console.log('  Inhalt: build.gradle.kts mit Kotlin-Plugin');
  console.log('  → Repo-Detection erwartet: stacks=[kotlin], indicators=[build.gradle.kts]');
  await addRepoThroughDirectoryBrowser(page, repoPath);

  const step3 = await page.evaluate((expectedPath) => {
    const card = document.querySelector(
      `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
    );
    return {
      cardCount: document.querySelectorAll(
        `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
      ).length,
      cardName: card ? card.querySelector('.lg-local-repo-name')?.textContent : null,
      cardPath: card ? card.querySelector('.lg-local-repo-path')?.textContent : null,
      hasRemoveBtn: card ? !!card.querySelector('.lg-local-repo-del') : null,
    };
  }, repoPath);
  console.log('  Karten erstellt:', step3.cardCount);
  console.log('  Card-Name:', step3.cardName);
  console.log('  Card-Pfad:', step3.cardPath);
  console.log('  Remove-Button:', step3.hasRemoveBtn ? '✓ vorhanden' : '✗ fehlt');
  expect(step3.cardCount).toBe(1);
  expect(step3.hasRemoveBtn).toBe(true);

  console.log('\n─── SCHRITT 4: CARD ANKLICKEN ───');
  await selectRepoCard(page, repoPath);

  const step4 = await page.evaluate((expectedPath) => {
    const card = document.querySelector(
      `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${expectedPath}"]`,
    );
    const matchPill = card?.querySelector('.lg-local-repo-match');
    return {
      cardSelected: card ? card.classList.contains('selected') : null,
      matchPillText: matchPill ? matchPill.textContent.trim() : null,
      matchPillType: matchPill ? matchPill.getAttribute('data-match') : null,
      toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
      btnDisabled: document.getElementById('lgBtnStartFix').disabled,
      btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
      lgStack: window._lgStack,
    };
  }, repoPath);
  console.log('  Card selected:', step4.cardSelected ? '✓ ja' : '✗ nein');
  console.log('  Match-Pill:', step4.matchPillText, '(type:', step4.matchPillType + ')');
  console.log('  Toolbar display:', JSON.stringify(step4.toolbarDisplay), step4.toolbarDisplay === '' ? '(visible)' : '');
  console.log('  Fix-Button:', step4.btnText);
  console.log('  Stack-Detection:', JSON.stringify(step4.lgStack));
  expect(step4.cardSelected).toBe(true);
  expect(step4.matchPillType).toBe('match');
  expect(step4.toolbarDisplay).toBe('');
  expect(step4.btnDisabled).toBe(false);

  console.log('\n─── SCHRITT 5: REPO ENTFERNEN ───');
  await removeRepoCard(page, repoPath);

  const step5 = await page.evaluate(() => ({
    cardCount: document.querySelectorAll('#lgLocalRepoList .lg-local-repo-card').length,
    toolbarDisplay: document.querySelector('.lg-fix-toolbar').style.display,
    btnText: document.getElementById('lgBtnStartFix').textContent.trim().replace(/\s+/g, ' '),
    selRepoId: window._lgFix ? window._lgFix.selectedRepoId : null,
  }));
  console.log('  Karten übrig:', step5.cardCount);
  console.log('  Toolbar display:', JSON.stringify(step5.toolbarDisplay));
  console.log('  Button text:', step5.btnText);
  console.log('  selectedRepoId:', step5.selRepoId);
  expect(step5.toolbarDisplay).toBe('none');
  expect(step5.selRepoId).toBe(null);

  console.log('\n╔══════════════════════════════════════════════════════════════╗');
  console.log('║                    DEMO ABGESCHLOSSEN                          ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');

});
