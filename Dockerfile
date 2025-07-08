# Build
FROM ubuntu:24.04 AS build
RUN apt update && apt install -y curl git unzip openjdk-17-jdk
WORKDIR /app
COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew shadowJar --no-daemon

# Runtime
FROM ubuntu:24.04
RUN apt-get update && \
    apt-get install -y openjdk-17-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* \

WORKDIR /app
COPY --from=build /app/build/libs/github-app.jar github-app.jar

# Run the app
CMD ["sh", "-c", "java -Djava.library.path=/usr/lib -jar github-app.jar"]
