FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn ./.mvn
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/event-knowledge-bot-*.jar /app/event-knowledge-bot.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/event-knowledge-bot.jar"]
