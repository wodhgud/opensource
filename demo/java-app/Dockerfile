FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# OpenTelemetry Java agent (traces / metrics / logs)
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
# Pyroscope OTel javaagent extension (async-profiler 포함): 프로파일러 시작 + span_id 라벨 / pyroscope.profile.id span 속성 연결
ADD https://github.com/grafana/otel-profiling-java/releases/latest/download/pyroscope-otel-javaagent-extension.jar /app/pyroscope-otel.jar
COPY --from=build /src/target/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-javaagent:/app/opentelemetry-javaagent.jar", \
  "-Dotel.javaagent.extensions=/app/pyroscope-otel.jar", \
  "-Dotel.pyroscope.start.profiling=true", \
  "-jar", "/app/app.jar"]
