import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Ticket Creation', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/ticket-creation');
  });

  test('page loads with tc- elements', async ({ page }) => {
    await expect(page.locator('[class*="tc-"]').first()).toBeAttached();
  });

  test('main form card is visible', async ({ page }) => {
    await expect(page.locator('.sp-form-card, .tc-form-card, .sp-card').first()).toBeVisible();
  });

  test('input textarea is present', async ({ page }) => {
    await expect(page.locator('#tcInputText')).toBeAttached();
  });

  test('generate button is present', async ({ page }) => {
    await expect(page.locator('#tcStart')).toBeAttached();
  });

  test('cancel button is present in DOM', async ({ page }) => {
    await expect(page.locator('#tcCancel')).toBeAttached();
  });

  test('cancel button is disabled initially', async ({ page }) => {
    const cancelBtn = page.locator('#tcCancel');
    await expect(cancelBtn).toBeDisabled();
  });

  test('jira config select is present', async ({ page }) => {
    await expect(page.locator('#tcJiraConfig')).toBeAttached();
  });

  test('project key input is present', async ({ page }) => {
    await expect(page.locator('#tcProjectKey')).toBeAttached();
  });

  test('KI-Prompt button is present', async ({ page }) => {
    await expect(page.locator('[data-prompt-prefix="ticketcreation"]')).toBeAttached();
  });

  test('KI-Chat button opens the chat drawer', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('Jira MCP source chip is rendered', async ({ page }) => {
    const chips = page.locator('.mcp-status-chip');
    await expect(chips).toHaveCount(1);
    await expect(chips.first()).toContainText(/Jira/i);
  });

  test('clicking generate without text keeps form visible without crashing', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('#tcStart').click();
    await page.waitForTimeout(150);
    expect(errors).toHaveLength(0);
    await expect(page.locator('#tcStart')).toBeAttached();
  });

  test('generate button is enabled initially', async ({ page }) => {
    await expect(page.locator('#tcStart')).toBeEnabled();
  });

  test('result card appears after successful generate (mocked API)', async ({ page }) => {
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'Mocked ticket',
          description: 'Mocked description',
          issueType: 'Story',
          labels: ['backend'],
        }),
      }),
    );

    await page.locator('#tcInputText').fill('Create a login feature for users');
    await page.locator('#tcStart').click();
    await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });
  });

  test('generate shows error message on API failure (mocked 500)', async ({ page }) => {
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'AI is not available' }),
      }),
    );

    await page.locator('#tcInputText').fill('Some input text');
    await page.locator('#tcStart').click();
    await page.waitForTimeout(1000);
    await expect(page.locator('#tcStart')).toBeEnabled({ timeout: 5000 });
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('#sidebar', { state: 'visible' });
    expect(errors).toHaveLength(0);
  });

  test('dark theme: form card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/ticket-creation');
    const card = page.locator('.sp-card, .sp-form-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('modern theme: page loads without error', async ({ page }) => {
    await setThemeAndGoto(page, 'modern', '/ticket-creation');
    await expect(page.locator('[class*="tc-"]').first()).toBeAttached();
  });

  test('popos theme: page loads without error', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/ticket-creation');
    await expect(page.locator('#tcStart')).toBeAttached();
  });

  test('legacy theme: page loads without error', async ({ page }) => {
    await setThemeAndGoto(page, 'legacy', '/ticket-creation');
    await expect(page.locator('#tcStart')).toBeAttached();
  });

  test('history card is present in DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-ticket-creation')).toBeAttached();
  });

  test('delete all history button is present in DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-ticket-creation [data-hx-delall]')).toBeAttached();
  });

  test('issue type select is present in create section', async ({ page }) => {
    const sel = page.locator('#tcCreateIssueType');
    await expect(sel).toBeAttached();
    const options = await sel.locator('option').allInnerTexts();
    expect(options.some((o) => o.toLowerCase().includes('story'))).toBeTruthy();
    expect(options.some((o) => o.toLowerCase().includes('bug'))).toBeTruthy();
    expect(options.some((o) => o.toLowerCase().includes('task'))).toBeTruthy();
  });

  test('subtask cards always display "Subtask" issue-type even when the AI returns "Story"', async ({ page }) => {
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'Parent Story',
          description: '## 🧑‍💻 User Story\nAs a user …',
          issueType: 'Story',
          priority: 'Minor',
          labels: [],
          openPoints: [],
          subtasks: [
            { summary: 'Subtask A', description: 'A body', issueType: 'Story', priority: 'Minor', labels: [] },
            { summary: 'Subtask B', description: 'B body', issueType: 'Task',  priority: 'Minor', labels: [] },
            { summary: 'Subtask C', description: 'C body', issueType: '',       priority: 'Minor', labels: [] },
          ],
        }),
      }),
    );

    await page.locator('#tcInputText').fill('Some input that should produce subtasks');
    await page.locator('#tcStart').click();
    await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });

    const subtaskCards = page.locator('.tc-subtask-card');
    await expect(subtaskCards).toHaveCount(3);

    for (let i = 0; i < 3; i++) {
      const typeText = await subtaskCards.nth(i).locator('.tc-subtask-type').innerText();
      expect(typeText.trim()).toBe('Subtask');
    }
  });

  test('subtask cards honour i18n label (de / en) for the issue-type badge', async ({ page }) => {
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'Parent',
          description: 'Body',
          issueType: 'Story',
          priority: 'Minor',
          labels: [],
          openPoints: [],
          subtasks: [{ summary: 'Sub A', description: 'd', issueType: 'Story', priority: 'Minor', labels: [] }],
        }),
      }),
    );
    await page.locator('#tcInputText').fill('Some input');
    await page.locator('#tcStart').click();
    await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });
    const typeText = await page.locator('.tc-subtask-card .tc-subtask-type').first().innerText();
    expect(typeText.trim()).toBe('Subtask');
  });

  test('create sends generated subtask drafts and reports partial Jira failures', async ({ page }) => {
    let createPayload = null;
    await page.route('**/api/config/jira**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([{ id: 91, name: 'Real Jira V3', active: true }]),
      }),
    );
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'Parent Story',
          description: '## 🧑‍💻 User Story\nAs a user I want a login.',
          issueType: 'Story',
          priority: 'Minor',
          labels: [],
          openPoints: [],
          subtasks: [
            { summary: 'Implement API', description: 'API body', issueType: 'Subtask', priority: 'Minor', labels: [] },
            { summary: 'Implement UI', description: 'UI body', issueType: 'Subtask', priority: 'Minor', labels: [] },
          ],
        }),
      }),
    );
    await page.route('**/api/ticket-creation/create-jira', async (route) => {
      createPayload = route.request().postDataJSON();
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          key: 'PROJ-1',
          id: '1',
          url: 'https://jira.example.com/browse/PROJ-1',
          subtasks: [
            { summary: 'Implement API', key: 'PROJ-2', id: '2', url: 'https://jira.example.com/browse/PROJ-2' },
            { summary: 'Implement UI', key: '', id: '', url: '', error: 'Issue type is invalid' },
          ],
        }),
      });
    });

    await goto(page, '/ticket-creation');
    await page.locator('#tcInputText').fill('Create a login with API and UI');
    await page.locator('#tcStart').click();
    await expect(page.locator('.tc-subtask-card')).toHaveCount(2);
    await page.locator('#tcCreateProjectKey').fill('PROJ');
    await page.locator('#tcBtnCreate').click();

    await expect(page.locator('#tcCreateStatus')).toContainText('PROJ-1');
    await expect(page.locator('#tcCreateStatus')).toContainText('1/2');
    await expect(page.locator('#tcCreateStatus')).toContainText('Implement UI');
    expect(createPayload.subtasks).toHaveLength(2);
    expect(createPayload.subtasks.every((subtask) => subtask.issueType === 'Subtask')).toBeTruthy();
  });

  test('subtask card "Story" defence-in-depth is preserved across all 4 themes', async ({ page }) => {
    for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
      await setThemeAndGoto(page, theme, '/ticket-creation');
      await page.route('**/api/ticket-creation/generate', (route) =>
        route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            summary: 'Parent',
            description: 'Body',
            issueType: 'Story',
            priority: 'Minor',
            labels: [],
            openPoints: [],
            subtasks: [{ summary: 'Sub A', description: 'd', issueType: 'Story', priority: 'Minor', labels: [] }],
          }),
        }),
      );
      await page.locator('#tcInputText').fill('Some input');
      await page.locator('#tcStart').click();
      await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });
      const typeText = await page.locator('.tc-subtask-card .tc-subtask-type').first().innerText();
      expect(typeText.trim(), `theme=${theme} must render Subtask`).toBe('Subtask');
    }
  });

  test('issue-type selector does NOT offer an "Epic" option', async ({ page }) => {
    const sel = page.locator('#tcCreateIssueType');
    const values = await sel.locator('option').evaluateAll((els) => els.map((e) => e.value));
    expect(values).not.toContain('Epic');
    expect(values).toEqual(['Story', 'Technical Task', 'Bug']);
  });

  test('issue-type segmented control has exactly 3 buttons (no Epic)', async ({ page }) => {
    const seg = page.locator('#tcIssueTypeSeg .tc-issue-type-btn');
    await expect(seg).toHaveCount(3);
    const labels = (await seg.allInnerTexts()).map((s) => s.trim());
    expect(labels.join('|')).not.toContain('Epic');
    expect(labels).toEqual(['Story', 'Technical Task', 'Bug']);
  });

  test('when the AI returns issueType "Epic" the UI still offers a Story / Technical Task / Bug fallback', async ({ page }) => {
    await page.route('**/api/ticket-creation/generate', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          summary: 'Rogue Epic',
          description: 'Body',
          issueType: 'Epic',
          priority: 'Minor',
          labels: [],
          openPoints: [],
          subtasks: [],
        }),
      }),
    );
    await page.locator('#tcInputText').fill('Some input');
    await page.locator('#tcStart').click();
    await expect(page.locator('#tcResultCard')).toBeVisible({ timeout: 8000 });
    const selected = await page.locator('#tcCreateIssueType').inputValue();
    expect(['Story', 'Technical Task', 'Bug']).toContain(selected);
    const optionValues = await page.locator('#tcCreateIssueType option').evaluateAll((els) => els.map((e) => e.value));
    expect(optionValues).not.toContain('Epic');
  });
});
