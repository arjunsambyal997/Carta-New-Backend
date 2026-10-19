FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app
USER app
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
