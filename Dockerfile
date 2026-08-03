FROM gradle:9.0.0-jdk21 AS build-env

WORKDIR /app
COPY . ./
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre

COPY --from=build-env /app/build/libs/github-app.jar /opt/githubapp/

WORKDIR /opt/githubapp

ENTRYPOINT ["java", "-jar", "github-app.jar"]
