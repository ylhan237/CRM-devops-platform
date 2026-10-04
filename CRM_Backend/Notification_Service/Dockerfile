# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY build-tools/pom.xml /build-tools/pom.xml
COPY checkstyle.xml /checkstyle.xml
COPY Notification_Service/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q
COPY Notification_Service/src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/Notification_Service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
