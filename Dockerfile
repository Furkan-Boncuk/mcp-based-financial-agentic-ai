FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

ARG MODULE

COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY conversation-service/pom.xml conversation-service/pom.xml
COPY financial-agent-service/pom.xml financial-agent-service/pom.xml

RUN mvn -B -pl "${MODULE}" -am dependency:go-offline

COPY api-gateway/src api-gateway/src
COPY auth-service/src auth-service/src
COPY conversation-service/src conversation-service/src
COPY financial-agent-service/src financial-agent-service/src

RUN mvn -B -pl "${MODULE}" -am package spring-boot:repackage -DskipTests
RUN find "${MODULE}/target" -maxdepth 1 -type f -name "${MODULE}-*.jar" ! -name "*.original" \
    -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /workspace/app.jar app.jar

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
