import { test, expect } from '@playwright/test';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';
import { goto } from './helpers.js';

test.describe('Ticket Creation – Demo Jira simulation', () => {

  test('Erstellen with Demo Jira produces a simulated ticket link in history', async ({ page, context }) => {
    // Mock generate to return a complete ticket
    await context.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'REST API für Reporting bereitstellen',
          description: 'Als Backend-Entwickler möchte ich einen REST-Endpunkt implementieren.',
          issueType: 'Story',
          labels: ['backend', 'api'],
          acceptanceCriteria: '- Endpunkt antwortet mit 200\n- Authentifizierung erforderlich',
          background: 'Dieses Feature ist Teil des Q2-Roadmaps.',
          openPoints: [],
        }),
      }),
    );

    // Mock Jira config list (Demo Jira with id=1)
    await context.route('**/api/config/jira**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: 'Demo Jira', active: true }]),
      }),
    );

    // Mock create-jira to return a simulated Demo ticket
    await context.route('**/api/ticket-creation/create-jira', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          key: 'DEMO-1042',
          id: 'DEMO',
          url: '/simulated-jira-ticket/99',
        }),
      }),
    );

    // History returns one entry with the jiraKey/jiraUrl already set (post-create state)
    await context.route('**/api/ticket-creation/history**', (route) => {
      const url = route.request().url();
      if (/\/history\/\d+$/.test(url)) {
        // single record fetch (for the simulated page)
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            id: 99,
            inputText: 'REST API für Reporting',
            summary: 'REST API für Reporting bereitstellen',
            issueType: 'Story',
            labels: 'backend,api',
            resultJson: JSON.stringify({
              summary: 'REST API für Reporting bereitstellen',
              description: 'Als Backend-Entwickler möchte ich einen REST-Endpunkt implementieren.',
              issueType: 'Story',
              labels: ['backend', 'api'],
              acceptanceCriteria: '- Endpunkt antwortet mit 200\n- Authentifizierung erforderlich',
              background: 'Dieses Feature ist Teil des Q2-Roadmaps.',
              openPoints: [],
            }),
            status: 'COMPLETED',
            jiraConfigId: 1,
            jiraProjectKey: 'DEMO',
            jiraKey: 'DEMO-1042',
            jiraUrl: '/simulated-jira-ticket/99',
            createdAt: '2026-05-26T20:15:00',
          }),
        });
      }
      // List: after create
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 99,
            inputText: 'REST API für Reporting',
            summary: 'REST API für Reporting bereitstellen',
            issueType: 'Story',
            labels: 'backend,api',
            resultJson: '{"summary":"REST API für Reporting bereitstellen"}',
            status: 'COMPLETED',
            jiraKey: 'DEMO-1042',
            jiraUrl: '/simulated-jira-ticket/99',
            createdAt: '2026-05-26T20:15:00',
          },
        ]),
      });
    });

    await goto(page, '/ticket-creation');

    // Start generation
    await page.locator('#tcInputText').fill('REST API für Reporting');
    await page.locator('#tcStart').click();

    // Wait for result card
    await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });

    // Check that the "Erstellen" button is present in the result card
    await expect(page.locator('#tcBtnCreate, button[data-i18n="ticket_creation.btn_create"]')).toBeVisible({ timeout: 3000 });

    // Set project key in the form so validation passes
    await page.locator('#tcCreateProjectKey').fill('DEMO');

    // Click Erstellen
    await page.locator('#tcBtnCreate').click();

    // Status should show the ticket key with a link
    await expect(page.locator('#tcCreateStatus')).toContainText('DEMO-1042', { timeout: 5000 });

    // History should contain the ticket key as a link
    await expect(page.locator('#hxCard-ticket-creation tbody')).toContainText('DEMO-1042', { timeout: 5000 });

    // The link in history should point to /simulated-jira-ticket/99
    const link = page.locator('#hxCard-ticket-creation tbody a.hx-tag[href*="simulated-jira-ticket"]');
    await expect(link).toBeVisible();
    const href = await link.getAttribute('href');
    expect(href).toMatch(/simulated-jira-ticket/);
  });

  test('Simulated Jira ticket page renders in Jira V3 style', async ({ page, context }) => {
    // Mock the history single-record API
    await context.route('**/api/ticket-creation/history/1', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          id: 1,
          inputText: 'REST API',
          summary: 'REST API für Reporting bereitstellen',
          issueType: 'Story',
          labels: 'backend,api',
          resultJson: JSON.stringify({
            summary: 'REST API für Reporting bereitstellen',
            description: 'Als Backend-Entwickler möchte ich einen REST-Endpunkt implementieren.',
            issueType: 'Story',
            labels: ['backend', 'api'],
            acceptanceCriteria: '- Endpunkt antwortet mit 200\n- Authentifizierung erforderlich',
            background: 'Dieses Feature ist Teil des Q2-Roadmaps.',
            openPoints: ['Welche Auth-Methode?'],
          }),
          status: 'COMPLETED',
          jiraKey: 'DEMO-1001',
          jiraProjectKey: 'DEMO',
          jiraUrl: '/simulated-jira-ticket/1',
          createdAt: '2026-05-26T20:15:00',
        }),
      }),
    );

    await page.goto(new URL('/simulated-jira-ticket/1', baseUrl).toString());

    // Jira nav should be visible
    await expect(page.locator('.jira-nav')).toBeVisible({ timeout: 5000 });

    // Ticket key in breadcrumb
    await expect(page.locator('#bc-key')).toContainText('DEMO-1001', { timeout: 5000 });

    // Summary rendered
    await expect(page.locator('#issue-title')).toContainText('REST API für Reporting bereitstellen');

    // Acceptance criteria section visible
    await expect(page.locator('#ac-section')).toBeVisible();

    // Background section visible
    await expect(page.locator('#bg-section')).toBeVisible();

    // Open points section visible
    await expect(page.locator('#op-section')).toBeVisible();

    // Labels rendered
    await expect(page.locator('.jira-label-pill').first()).toBeVisible();

    // Simulated badge
    await expect(page.locator('.jira-sim-badge')).toContainText('Simuliert');
  });

});
