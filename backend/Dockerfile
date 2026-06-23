FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S crm && adduser -S crm -G crm
COPY --from=build /workspace/target/crm-backend-0.0.1-SNAPSHOT.jar /app/crm-backend.jar
USER crm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/crm-backend.jar"]
