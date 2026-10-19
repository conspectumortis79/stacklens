import http from 'http';
import https from 'https';
import { refreshCdnFixtures } from './cdn-fixtures.js';

export default async function globalSetup() {
  const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';
  const fixtures = await refreshCdnFixtures();
  console.log(
    `\n📦  CDN fixtures: ${fixtures.served}/${fixtures.total} served locally `
    + `(${fixtures.downloaded} downloaded, ${fixtures.missing} falling back to the network)\n`,
  );
  await checkServer(baseUrl);
}

function checkServer(url) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('https') ? https : http;

    const req = mod.get(url, { timeout: 5000 }, (res) => {
      res.resume();
      console.log(`\n✅  StackLens server reachable at ${url} (HTTP ${res.statusCode})\n`);
      resolve();
    });

    req.on('timeout', () => {
      req.destroy();
      reject(serverDownError(url, 'connection timed out after 5 s'));
    });

    req.on('error', (err) => {
      const msg = err.code ? `${err.code}: ${err.message}` : err.message;
      reject(serverDownError(url, msg));
    });
  });
}

function serverDownError(url, reason) {
  return new Error(
    `\n`
    + `╔═══════════════════════════════════════════════════════════════╗\n`
    + `║  ❌  StackLens server not reachable                           ║\n`
    + `╠═══════════════════════════════════════════════════════════════╣\n`
    + `║  URL    : ${url.padEnd(52)}║\n`
    + `║  Reason : ${reason.slice(0, 52).padEnd(52)}║\n`
    + `╠═══════════════════════════════════════════════════════════════╣\n`
    + `║  Start the server first, then re-run the tests:               ║\n`
    + `║                                                               ║\n`
    + `║    ./gradlew bootRun          (opens the desktop window)      ║\n`
    + `║                                                               ║\n`
    + `║  Then run:                                                    ║\n`
    + `║    ./gradlew testE2e                                          ║\n`
    + `║    npx playwright test                                        ║\n`
    + `╚═══════════════════════════════════════════════════════════════╝\n`,
  );
}
