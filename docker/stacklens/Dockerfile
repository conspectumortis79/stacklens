FROM node:22-slim AS mcp-builder
WORKDIR /app
COPY mcp-server/package.json mcp-server/package-lock.json ./
RUN npm ci --no-audit --no-fund --ignore-scripts --omit=optional
COPY mcp-server/tsconfig.json ./
COPY mcp-server/src ./src
RUN npm run build && npm prune --omit=dev

FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace
ENV GRADLE_OPTS="-Xmx10g -XX:MaxMetaspaceSize=1g"
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew dependencies --no-daemon -q 2>&1 | tail -5 || true
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre-noble

ARG KOTLIN_VERSION=2.1.21
ARG K6_VERSION=2.2.0

LABEL org.opencontainers.image.title=StackLens
LABEL org.opencontainers.image.description="AI-powered developer productivity tool"
LABEL org.opencontainers.image.source=https://github.com/your-org/stacklens

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        git \
        unzip \
        ca-certificates \
        gnupg \
        tesseract-ocr \
        tesseract-ocr-deu \
        tesseract-ocr-eng \
        libxi6 \
        libxrender1 \
        libxtst6 \
        libxext6 \
        libx11-6 \
        libxcb1 \
        xvfb \
    && rm -rf /var/lib/apt/lists/* \
    && tesseract --version \
    && tesseract --list-langs | head -5

RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
         -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
         https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
         > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/* \
    && docker --version \
    && docker compose version

RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] \
        https://cli.github.com/packages stable main" \
      | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
    && apt-get update \
    && apt-get install -y gh \
    && rm -rf /var/lib/apt/lists/* \
    && gh --version

RUN curl -fsSL \
      "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
      -o /tmp/kotlin.zip \
    && unzip -q /tmp/kotlin.zip -d /opt \
    && rm /tmp/kotlin.zip \
    && ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc \
    && ln -sf /opt/kotlinc/bin/kotlin   /usr/local/bin/kotlin \
    && kotlinc -version

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
        amd64)   K6_ASSET="k6-v${K6_VERSION}-linux-amd64.tar.gz" ;; \
        arm64)   K6_ASSET="k6-v${K6_VERSION}-linux-arm64.tar.gz" ;; \
        *) echo "Unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
    esac; \
    mkdir -p /opt/k6; \
    curl -fsSL \
      "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/${K6_ASSET}" \
      -o /tmp/k6.tar.gz; \
    tar -xzf /tmp/k6.tar.gz -C /opt/k6 --strip-components=1; \
    install -m 0755 /opt/k6/k6 /usr/local/bin/k6; \
    rm -rf /tmp/k6.tar.gz /opt/k6; \
    k6 version

RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && node --version

RUN groupadd -g 999 docker \
    && useradd -m -s /bin/bash stacklens \
    && usermod -aG docker stacklens \
    && mkdir -p /home/stacklens/.stacklens/data/tools \
    && chown -R stacklens:stacklens /home/stacklens/.stacklens

WORKDIR /home/stacklens

COPY --from=mcp-builder /app/dist         mcp-server/dist
COPY --from=mcp-builder /app/node_modules mcp-server/node_modules
COPY --from=mcp-builder /app/package.json mcp-server/package.json
RUN chown -R stacklens:stacklens mcp-server

COPY --from=builder /workspace/build/libs/stacklens-*.jar app.jar
RUN chown stacklens:stacklens app.jar

COPY docker/stacklens/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8090
ENTRYPOINT ["/entrypoint.sh"]
