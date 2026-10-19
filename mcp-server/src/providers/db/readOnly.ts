const READ_ONLY = /^\s*(select|with|show|describe|desc|explain)\b/i;

export function assertReadOnly(sql: string): void {
  if (!READ_ONLY.test(sql)) {
    throw new Error("only read-only statements are allowed (SELECT/WITH/SHOW/DESCRIBE/EXPLAIN)");
  }
  if (!allowsStatementCount(sql)) {
    throw new Error("multiple statements are not allowed");
  }
}

function allowsStatementCount(sql: string): boolean {
  return !sql.replace(/;\s*$/, "").includes(";");
}
