# syntax=docker/dockerfile:1
# Build the application JAR first. Its classpath must include the Booster shaded JAR.
FROM debian:bookworm-slim AS jdk
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*
COPY scripts/install-jdk.sh /bootstrap/scripts/install-jdk.sh
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export CURL_CA_BUNDLE=/run/secrets/proxy_ca; fi; \
    BOOSTER_TOOLS_DIR=/opt/java sh /bootstrap/scripts/install-jdk.sh

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates libstdc++6 zlib1g && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home booster
COPY --from=jdk /opt/java/jdk-27.0.1.0.3 /opt/java
ENV JAVA_HOME=/opt/java PATH=/opt/java/bin:$PATH PORT=8080
WORKDIR /app
COPY --chown=booster:booster --chmod=0444 target/booster-java-*-all.jar /app/booster.jar
# Extend this image and COPY your application JAR into /app/ for your own domain.
# The bundled public shopping-cart example is available without an additional JAR.
USER booster
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-cp", "/app/*", "io.boostercloud.booster.azure.hosting.AzureHost"]
