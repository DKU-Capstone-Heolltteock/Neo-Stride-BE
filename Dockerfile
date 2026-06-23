FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre AS webp-tools
RUN apt-get update \
    && apt-get install -y --no-install-recommends webp \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /webp/bin /webp/lib \
    && cp /usr/bin/cwebp /webp/bin/cwebp \
    && ldd /usr/bin/cwebp | awk '$3 ~ /^\// { print $3 }' | xargs -I '{}' cp -L '{}' /webp/lib/

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=webp-tools /webp/bin/cwebp /usr/bin/cwebp
COPY --from=webp-tools /webp/lib/ /usr/local/lib/
ENV LD_LIBRARY_PATH=/usr/local/lib

ARG APP_UID=10001
ARG APP_GID=10001
RUN addgroup --system --gid ${APP_GID} spring \
    && adduser --system --uid ${APP_UID} --ingroup spring spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
