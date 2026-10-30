# The build stage runs on the CI machine's own platform (bytecode is the same everywhere), so a multi-arch image for
# the Arm production server builds at native speed. Only the small runtime stage is per platform.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd -r -u 1001 app
USER app
COPY --from=build /app.jar /app.jar
EXPOSE 8080
# Liveness only: Docker restarts a hung JVM. Readiness (database included) is what deploy.sh waits for.
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness > /dev/null || exit 1
ENTRYPOINT ["java","-jar","/app.jar"]
