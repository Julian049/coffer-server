# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.10_7-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && mv target/*.jar app.jar

FROM eclipse-temurin:21.0.10_7-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 coffer
COPY --from=build /workspace/app.jar app.jar
USER coffer
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
