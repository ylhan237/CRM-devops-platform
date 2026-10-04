# Stage 1: Build
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY build-tools/pom.xml /build-tools/pom.xml
COPY checkstyle.xml /checkstyle.xml
COPY Student_Management_Service/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q
COPY Student_Management_Service/src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/Application_Management_Service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
