# Multi-stage build - must be run with context set to project ROOT, not service subdirectory
# docker-compose.yml build context is updated to use root accordingly

FROM maven:3.9-eclipse-temurin-11 AS builder
WORKDIR /app

# Copy root pom (provides dependency management as the parent POM).
COPY pom.xml ./pom.xml

# The root POM is also an aggregator listing all four modules. Since we only
# copy one service into this image, strip the <modules> block so Maven doesn't
# fail looking for the other module directories.
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# Copy only the target service (build arg decides which one).
ARG SERVICE_NAME
COPY ${SERVICE_NAME}/pom.xml ./${SERVICE_NAME}/pom.xml
COPY ${SERVICE_NAME}/src ./${SERVICE_NAME}/src

# Build just this service against the parent POM.
RUN mvn -f ${SERVICE_NAME}/pom.xml clean package -DskipTests -q

# openjdk:11-jre-slim was removed from Docker Hub; eclipse-temurin is the
# maintained replacement.
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

# curl is required by the container healthchecks defined in docker-compose.yml
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG SERVICE_NAME
COPY --from=builder /app/${SERVICE_NAME}/target/*.jar app.jar

# Services listen on 8080 (gateway) / 8081 / 8082 / 8083 depending on SERVICE_NAME
EXPOSE 8080 8081 8082 8083
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
