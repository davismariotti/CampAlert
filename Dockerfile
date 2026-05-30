FROM gradle:jdk21-alpine AS build

ARG VERSION

WORKDIR /src

COPY . .

RUN ./gradlew assemble -Pversion=$VERSION

RUN cp ./build/libs/CampAlert-$VERSION.jar /src/build/app.jar

FROM eclipse-temurin:21-jre AS app

ENV SPRING_PROFILES_ACTIVE docker,log-json

COPY --from=build /src/build/app.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
