FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY finance-data ./finance-data
COPY finance-security ./finance-security
COPY finance-service ./finance-service
COPY finance-ai ./finance-ai
COPY finance-app ./finance-app
RUN mvn -q -pl finance-app -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/finance-app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
