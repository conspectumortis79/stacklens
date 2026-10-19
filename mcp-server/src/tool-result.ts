export type ToolResult = {
  content: { type: "text"; text: string }[];
  isError?: boolean;
};

export function ok(data: unknown): ToolResult {
  const text = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  return { content: [{ type: "text" as const, text }] };
}

export function fail(message: string): ToolResult {
  return { isError: true, content: [{ type: "text" as const, text: message }] };
}

export async function safeCall<T>(fn: () => Promise<T>): Promise<ToolResult> {
  try {
    return ok(await fn());
  } catch (e) {
    const msg = (e as Error).message || String(e) || "unknown error";
    return fail(msg);
  }
}

export async function safePing(provider: string, fn: () => Promise<unknown>): Promise<ToolResult> {
  try {
    return ok({ ok: true, provider, detail: await fn() });
  } catch (e) {
    const msg = (e as Error).message || String(e) || "unknown error";
    return {
      isError: true,
      content: [
        { type: "text" as const, text: JSON.stringify({ ok: false, provider, detail: msg }, null, 2) },
      ],
    };
  }
}
