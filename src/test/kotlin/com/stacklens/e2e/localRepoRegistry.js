import { request } from '@playwright/test';

export async function createLocalRepo(baseUrl, { name, path, scope }) {
  const ctx = await request.newContext({ baseURL: baseUrl });
  try {
    const res = await ctx.post('/api/local-repos', {
      data: { id: null, name, path, active: true, scope },
    });
    if (!res.ok()) {
      throw new Error(`POST /api/local-repos failed with ${res.status()}: ${await res.text()}`);
    }
    return await res.json();
  } finally {
    await ctx.dispose();
  }
}

export async function deleteLocalRepoById(baseUrl, id) {
  const ctx = await request.newContext({ baseURL: baseUrl });
  try {
    await ctx.delete(`/api/local-repos/${id}`);
  } finally {
    await ctx.dispose();
  }
}

export async function listLocalRepos(baseUrl) {
  const ctx = await request.newContext({ baseURL: baseUrl });
  try {
    const res = await ctx.get('/api/local-repos');
    if (!res.ok()) return [];
    return await res.json();
  } finally {
    await ctx.dispose();
  }
}

export async function deleteLocalReposByPath(baseUrl, repoPath) {
  const repos = await listLocalRepos(baseUrl);
  const ctx = await request.newContext({ baseURL: baseUrl });
  try {
    for (const repo of repos) {
      if (repo && repo.path === repoPath) {
        await ctx.delete(`/api/local-repos/${repo.id}`);
      }
    }
  } finally {
    await ctx.dispose();
  }
}

export async function deleteLocalReposByScope(baseUrl, scope) {
  const repos = await listLocalRepos(baseUrl);
  const ctx = await request.newContext({ baseURL: baseUrl });
  try {
    for (const repo of repos) {
      if (repo && repo.scope === scope) {
        await ctx.delete(`/api/local-repos/${repo.id}`);
      }
    }
  } finally {
    await ctx.dispose();
  }
}
