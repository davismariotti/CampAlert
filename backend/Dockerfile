FROM eclipse-temurin:21-jre

ENV SPRING_PROFILES_ACTIVE=prod,docker,log-json

COPY build/libs/app.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
