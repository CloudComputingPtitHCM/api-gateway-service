# Stage 1: Build with Maven
FROM maven:3.9.2-eclipse-temurin-17 AS build

WORKDIR /app

# Copy file pom.xml và code
COPY pom.xml .
COPY src ./src

# Build project
RUN mvn clean package -DskipTests

# Stage 2: Run application
FROM openjdk:25-ea-17-slim

WORKDIR /app

# Copy jar từ stage build
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
