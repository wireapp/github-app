FROM gradle:9.0.0-jdk21 AS build-env

WORKDIR /app
COPY . ./
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre

# Workaround for CVE-2026-114938 (Java4Shell, WPB-30191): disable the AVX-512 Base64 intrinsic.
# Remove once the image runs Temurin >= 21.0.13.1.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

COPY --from=build-env /app/build/libs/github-app.jar /opt/githubapp/

WORKDIR /opt/githubapp

ENTRYPOINT ["java", "-jar", "github-app.jar"]
