import { expect } from '@playwright/test';

function repoCard(page, repoPath) {
  return page.locator(`#lgLocalRepoList .lg-local-repo-card[data-repo-path="${repoPath}"]`);
}

export async function addRepoThroughDirectoryBrowser(page, repoPath) {
  await page.evaluate((dir) => {
    window.__kiChatOpenDirBrowser = (cb) => setTimeout(() => cb(dir), 50);
  }, repoPath);
  await page.locator('button.lg-repo-add-btn').click();
  await expect(repoCard(page, repoPath)).toHaveCount(1, { timeout: 10_000 });
}

export async function selectRepoCard(page, repoPath) {
  const card = repoCard(page, repoPath);
  await card.click();
  await expect(card).toHaveClass(/selected/);
  await expect(card.locator('.lg-local-repo-match')).toBeVisible();
}

export async function removeRepoCard(page, repoPath) {
  const card = repoCard(page, repoPath);
  await card.locator('.lg-local-repo-del').click();
  await expect(card).toHaveCount(0);
}
