FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM tomcat:10.1-jdk21-temurin
# OpenTelemetry Java agent + Pyroscope OTel extension (span profiles)
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /usr/local/tomcat/otel/opentelemetry-javaagent.jar
ADD https://github.com/grafana/otel-profiling-java/releases/latest/download/pyroscope-otel-javaagent-extension.jar /usr/local/tomcat/otel/pyroscope-otel.jar
COPY --from=build /src/target/tomcat-app.war /usr/local/tomcat/webapps/tomcat-app.war
# catalina.sh 가 JAVA_OPTS 를 JVM 옵션으로 사용
ENV JAVA_OPTS="-javaagent:/usr/local/tomcat/otel/opentelemetry-javaagent.jar -Dotel.javaagent.extensions=/usr/local/tomcat/otel/pyroscope-otel.jar -Dotel.pyroscope.start.profiling=true"
EXPOSE 8080
