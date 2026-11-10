FROM node:24.22.2-bookworm-slim@sha256:01d566e8d94d8c619591d78652babfccd3c6992a3dffa9ed5ab39e11cf6c70e9 AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -B -ntp dependency:go-offline
COPY backend/ backend/
COPY --from=frontend /build/frontend/dist frontend/dist
RUN mvn -f backend/pom.xml -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy@sha256:2a9eb226abb654c9d1f91b53cf638d20cb3abb726d1b18a67909c29515545469
WORKDIR /app
RUN groupadd --gid 10001 cutledger && useradd --uid 10001 --gid cutledger --no-create-home cutledger
COPY --from=backend --chown=cutledger:cutledger /build/backend/target/cutledger-0.1.0.jar /app/app.jar
USER 10001:10001
ENV PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT}; printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3; head -1 <&3 | grep -q '200'"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
