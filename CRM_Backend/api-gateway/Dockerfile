# Stage 1: Build
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY build-tools/pom.xml /build-tools/pom.xml
COPY checkstyle.xml /checkstyle.xml
COPY api-gateway/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q
COPY api-gateway/src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8060
ENTRYPOINT ["java", "-jar", "app.jar"]
