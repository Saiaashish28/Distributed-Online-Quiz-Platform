# Multi-stage build: compile with the Maven wrapper, run on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/quizsphere-backend-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 quizsphere
COPY --from=build /app/app.jar app.jar
USER quizsphere
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
