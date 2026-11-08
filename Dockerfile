FROM eclipse-temurin:22-jdk AS buildstage 

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY pom.xml .
COPY src /app/src
RUN mvn clean package

FROM eclipse-temurin:22-jdk

COPY --from=buildstage /app/target/ms-administracion-archivos-1.0.0.jar /app/app.jar

EXPOSE 8080

RUN mkdir -p /app/efs

CMD ["java", "-jar", "/app/app.jar"]