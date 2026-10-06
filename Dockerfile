# Multi-stage build: JDK + Maven wrapper to build, JRE only to run.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, so source-only changes reuse this layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/budget-app-backend-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
USER app

# Layered copy: dependencies change least often, application classes most often.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
