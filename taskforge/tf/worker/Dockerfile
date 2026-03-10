FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S taskforge && adduser -S taskforge -G taskforge
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown taskforge:taskforge app.jar
USER taskforge
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java","-jar","-Xmx256m","-Xms128m","app.jar"]
