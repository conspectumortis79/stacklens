import { describe, it, expect } from 'vitest';
import * as H from './support/pageModelHarness.js';

const ROUTES = H.kotlinRoutes();
const SOURCES = [...H.templateFiles(), ...H.ownScriptFiles()]
  .map((file) => ({ file, text: H.read(file) }));
const URLS = H.frontendUrlLiterals(SOURCES);

function segments(path) {
  return path.split('?')[0].split('#')[0].split('/').filter(Boolean);
}

function compatible(front, route) {
  if (front.length > route.length) return false;
  for (let i = 0; i < front.length; i += 1) {
    const a = front[i];
    const b = route[i];
    if (a.startsWith('{') || b.startsWith('{')) continue;
    if (a !== b) return false;
  }
  return true;
}

export function resolveRoute(url) {
  const front = segments(url.replace(/\{X\}/g, '{v}'));
  return ROUTES.some((route) => compatible(front, segments(route)));
}

describe('route contract: every frontend url is served by a controller', () => {
  it('controller routes were discovered', () => {
    expect(ROUTES.length).toBeGreaterThan(200);
  });

  it('no frontend url literal lacks a route', () => {
    const unmatched = [];
    for (const [url, where] of URLS) {
      if (resolveRoute(url)) continue;
      unmatched.push(`${url} (${where.file}:${where.line})`);
    }
    expect(unmatched.sort()).toEqual([]);
  });

  it('matches a literal route and a path-variable route', () => {
    expect(resolveRoute('/api/prompts')).toBe(true);
    expect(resolveRoute('/api/ai-usage/kichat/12')).toBe(true);
  });

  it('rejects a typo of a known route', () => {
    expect(resolveRoute('/api/code-analysis/gitlb/{X}')).toBe(false);
    expect(resolveRoute('/api/searchtt/{X}')).toBe(false);
  });
});
