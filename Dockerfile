FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/revanced-external-bundles-all.jar app.jar
COPY build/patcher-runtimes patcher-runtimes
ENV BACKEND_PATCHER_RUNTIME_DIR=/app/patcher-runtimes
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
