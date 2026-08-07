FROM maven:3.9.16-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl tongluxing-app -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache tini \
    && addgroup -S spring \
    && adduser -S -G spring spring

WORKDIR /app

COPY --from=build --chown=spring:spring \
    /workspace/tongluxing-app/target/tongluxing-app-1.0-SNAPSHOT.jar \
    /app/tongluxing-app.jar

USER spring:spring

EXPOSE 18080

ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/tongluxing-app.jar"]
