# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY config config
# Make gradlew executable
RUN chmod +x gradlew
# Download dependencies
RUN ./gradlew dependencies

# Copy source code and resources
COPY src src
# Build the application
RUN ./gradlew build -x test -x checkstyleMain -x checkstyleTest

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built artifact from builder stage
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE ${PORT:-8080}
ENTRYPOINT ["java", "-jar", "app.jar"]