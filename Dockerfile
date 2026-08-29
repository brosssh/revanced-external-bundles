FROM gradle:8-jdk21 AS build

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

ENV GITHUB_ACTOR=$GITHUB_ACTOR
ENV GITHUB_TOKEN=$GITHUB_TOKEN

WORKDIR /app
COPY . .
RUN gradle startShadowScript --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/revanced-external-bundles-all.jar app.jar
COPY --from=build /app/build/patcher-runtimes patcher-runtimes
ENV BACKEND_PATCHER_RUNTIME_DIR=/app/patcher-runtimes
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
