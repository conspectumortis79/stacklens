FROM node:22-slim AS builder
WORKDIR /app
COPY package.json package-lock.json ./
# --ignore-scripts: skips esbuild's postinstall script (node install.js).
# --omit=optional: skips downloading @esbuild/linux-x64 and all other optional
# platform packages entirely. npm ci downloads optional packages by default even
# though --ignore-scripts prevents the postinstall from running; that ~8 MB binary
# download stalls the build in restricted Docker network environments.
# The tsc build ("npm run build") needs only the typescript package, not esbuild.
RUN npm ci --no-audit --no-fund --ignore-scripts --omit=optional
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

FROM node:22-slim
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY package.json ./
EXPOSE 7456
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD node -e "fetch('http://127.0.0.1:7456/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "dist/index.js"]
