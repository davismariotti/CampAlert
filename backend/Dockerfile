FROM eclipse-temurin:21-jre

ENV SPRING_PROFILES_ACTIVE=docker,log-json

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl \
    && curl -fSL https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip -o /tmp/newrelic-java.zip \
    && unzip /tmp/newrelic-java.zip -d / \
    && rm /tmp/newrelic-java.zip \
    && apt-get purge -y --auto-remove unzip curl \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/app.jar /app.jar

ENTRYPOINT ["java", "-javaagent:/newrelic/newrelic.jar", "-jar", "/app.jar"]
