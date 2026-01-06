FROM eclipse-temurin:17-jre as base
WORKDIR /app

ARG JAR_FILE=target/rainet-oss-0.1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
