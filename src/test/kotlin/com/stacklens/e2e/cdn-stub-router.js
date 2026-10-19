import { CDN_HOSTS, fixtureKey, loadCdnFixtures } from './cdn-fixtures.js';

const installations = new WeakMap();

async function stubContext(context) {
  const fixtures = await loadCdnFixtures();
  if (fixtures.size === 0) return;
  for (const host of CDN_HOSTS) {
    await context.route(`${host}/**`, async (route) => {
      const fixture = fixtures.get(fixtureKey(route.request().url()));
      if (!fixture) {
        await route.continue();
        return;
      }
      await route.fulfill({ status: 200, contentType: fixture.contentType, body: fixture.body });
    });
  }
}

export function installCdnStubs(context) {
  let installation = installations.get(context);
  if (!installation) {
    installation = stubContext(context).catch((err) => {
      installations.delete(context);
      console.warn(`cdn stubs disabled for this context: ${err?.message ?? err}`);
    });
    installations.set(context, installation);
  }
  return installation;
}
