FROM maven:3.9.11-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S gametrust && adduser -S gametrust -G gametrust
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER gametrust
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
