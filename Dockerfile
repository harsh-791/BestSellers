FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 1000 app \
    && useradd --system --uid 1000 --gid 1000 --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /tmp \
    && chown app:app /app /tmp
WORKDIR /app
COPY --from=build --chown=app:app /src/target/bestsellers.jar /app/bestsellers.jar
USER 1000
EXPOSE 8080
ENV SERVER_PORT=8080 \
    FEATURE_PRICE_LOOKUP=true \
    JAVA_TOOL_OPTIONS="-Duser.home=/tmp -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["java", "-jar", "/app/bestsellers.jar"]
