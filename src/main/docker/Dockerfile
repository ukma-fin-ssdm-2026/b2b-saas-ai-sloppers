# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# tests and linters run in CI, the image only packages the jar
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests -Dcheckstyle.skip -Dspotbugs.skip

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
