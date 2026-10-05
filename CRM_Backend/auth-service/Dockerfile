# Stage 1: Build
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY build-tools/pom.xml /build-tools/pom.xml
COPY checkstyle.xml /checkstyle.xml
COPY auth-service/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q
COPY auth-service/src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
# Alpine would be smaller by about 100 MB and is amd64-only, which
# no multi-platform build can use: every temurin alpine tag is amd64 only.
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/Auth_Api1-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
