FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --gid 10001 qualifica \
    && useradd --uid 10001 --gid qualifica --home-dir /app \
        --no-create-home --shell /usr/sbin/nologin qualifica \
    && chown qualifica:qualifica /app

COPY --from=build --chown=qualifica:qualifica /build/target/*.jar /app/app.jar

USER qualifica
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
