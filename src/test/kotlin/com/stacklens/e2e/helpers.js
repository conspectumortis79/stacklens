import { installCdnStubs } from './cdn-stub-router.js';

export async function goto(page, path) {
  await installCdnStubs(page.context());
  await page.goto(path, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.waitForSelector('#sidebar', { state: 'visible', timeout: 15_000 });
}

export async function setTheme(page, theme) {
  await installCdnStubs(page.context());
  await page.evaluate((t) => localStorage.setItem('stacklens-theme', t), theme);
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.waitForSelector('#sidebar', { state: 'visible', timeout: 15_000 });
}

export async function setThemeAndGoto(page, theme, path = '/') {
  await installCdnStubs(page.context());
  await page.addInitScript((t) => {
    try { localStorage.setItem('stacklens-theme', t); } catch (_) {}
  }, theme);
  await page.goto(path, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.waitForSelector('#sidebar', { state: 'visible', timeout: 15_000 });
}

export async function getTheme(page) {
  return page.evaluate(() => document.documentElement.getAttribute('data-theme'));
}

export function installPageErrorFilter(page) {
  const errors = [];
  page.on('pageerror', (err) => {
    if (err?.message && /viewer\.diagrams\.net/.test(err.message)) return;
    errors.push(err.message);
  });
  return errors;
}

export const ALL_PAGES = [
  { path: '/security-analysis', name: 'Security',       rootSelector: '[class*="ba-"]' },
  { path: '/code-analysis',     name: 'Code',           rootSelector: '[class*="ca-"]' },
  { path: '/grafana-analysis',  name: 'Grafana',        rootSelector: '[class*="ga-"]' },
  { path: '/jwt-analysis',      name: 'JWT',            rootSelector: '.jwt-page' },
  { path: '/lasttest-analysis', name: 'Load Test',      rootSelector: '[class*="lt-"]' },
  { path: '/pipeline-analysis', name: 'Pipeline',       rootSelector: '[class*="pa-"]' },
  { path: '/review-analysis',   name: 'Review',         rootSelector: '[class*="ra-"]' },
  { path: '/sql-analysis',      name: 'SQL',            rootSelector: '[class*="sa-"]' },
  { path: '/logging',           name: 'Logging',        rootSelector: '[class*="lg-"]' },
  { path: '/ticket-implementation',   name: 'Ticket',         rootSelector: '[class*="ta-"]' },
  { path: '/tooling',           name: 'Tooling',        rootSelector: '[class*="tl-"]' },
  { path: '/search',            name: 'Search',         rootSelector: '[class*="su-"]' },
  { path: '/docs',              name: 'Docs',           rootSelector: '.doc-tabs' },
  { path: '/config',            name: 'Configuration',  rootSelector: '[class*="cf-"]' },
];
