import { request } from "undici";

/** Generic HTTP helper used by every upstream provider. */
export async function httpJson(
  method: string,
  url: string,
  headers: Record<string, string>,
  body?: unknown,
): Promise<unknown> {
  const res = await request(url, {
    method: method as never,
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.body.text();
  if (res.statusCode >= 400) {
    throw new Error(`HTTP ${res.statusCode} ${method} ${url}: ${text.slice(0, 500)}`);
  }
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export function basicAuth(user: string, password: string): string {
  return "Basic " + Buffer.from(`${user}:${password}`).toString("base64");
}

export function bearer(token: string): string {
  return `Bearer ${token}`;
}

export function trimSlash(s: string): string {
  return s.replace(/\/+$/, "");
}
