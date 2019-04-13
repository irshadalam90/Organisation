FROM openjdk:8-jdk-alpine

RUN apk update && apk add bash

VOLUME /tmp

EXPOSE 8085

ARG JAR_FILE=target/organisation-service-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]