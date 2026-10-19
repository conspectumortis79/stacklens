import { defineConfig, devices } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
const e2eDataDir = path.resolve(repoRoot, 'build/e2e-stacklens-data');
const defaultBaseUrl = 'http://127.0.0.1:8091';
const baseUrl = process.env.BASE_URL || defaultBaseUrl;
const javaBin = process.env.E2E_JAVA_BIN ||
  (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java');
const serverPort = new URL(baseUrl).port || '80';
const serverHeapSize = '3g';
const ciWorkerCount = 1;
const localWorkerCount = 4;
const isolatedE2eTarget = baseUrl === defaultBaseUrl;
const sharedServerOptIn = process.env.E2E_ALLOW_SHARED_SERVER === 'true';
const e2eDatasourceUrl =
  `jdbc:h2:file:${e2eDataDir}/stacklens;MODE=PostgreSQL;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE`;

if (!isolatedE2eTarget && !sharedServerOptIn) {
  throw new Error(
    `BASE_URL "${baseUrl}" is not the isolated e2e instance "${defaultBaseUrl}". ` +
    'The e2e suite deletes server-side data: POST /api/admin/factory-reset-db wipes every ' +
    'configuration and analysis, and other specs delete all MCP servers, search sessions and ' +
    'analysis histories. Unset BASE_URL so Playwright starts its own server against ' +
    `"${e2eDataDir}", or set E2E_ALLOW_SHARED_SERVER=true to accept that this run deletes the ` +
    'data of that server.',
  );
}

export default defineConfig({
  testDir: './src/test/kotlin/com/stacklens/e2e',
  fullyParallel: false,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? ciWorkerCount : localWorkerCount,
  reporter: [
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['list'],
  ],

  use: {
    baseURL: baseUrl,
    headless: true,
    viewport: { width: 1280, height: 800 },
    locale: 'de-DE',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  timeout: 30_000,
  expect: { timeout: 8_000 },

  webServer: {
    command: `SERVER_PORT=${serverPort} STACKLENS_DATA_DIR="${e2eDataDir}" SPRING_DATASOURCE_URL="${e2eDatasourceUrl}" "${javaBin}" -Xmx${serverHeapSize} -jar build/libs/stacklens-0.0.1-SNAPSHOT.jar`,
    url: baseUrl,
    timeout: 120_000,
    reuseExistingServer: isolatedE2eTarget || sharedServerOptIn,
    env: {
      ...process.env,
      STACKLENS_DATA_DIR: e2eDataDir,
      SPRING_DATASOURCE_URL: e2eDatasourceUrl,
      STACKLENS_SEED_DEMO_DATA: 'true',
      SPRING_PROFILES_ACTIVE: ['test-fixtures', process.env.SPRING_PROFILES_ACTIVE]
        .filter(Boolean)
        .join(','),
    },
    stdout: 'pipe',
    stderr: 'pipe',
  },

  globalSetup: './src/test/kotlin/com/stacklens/e2e/global-setup.js',
});
