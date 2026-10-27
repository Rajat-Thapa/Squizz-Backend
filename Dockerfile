FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY subscription-service subscription-service
COPY notification-service notification-service
ARG SERVICE=subscription-service
RUN mvn -B -pl ${SERVICE} -am package -DskipTests && cp ${SERVICE}/target/${SERVICE}-1.0.0.jar /app.jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-jar","/app/app.jar"]
