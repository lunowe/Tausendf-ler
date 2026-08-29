# Telegram-Bot: reiner Client (Long-Polling zu Telegram, REST zum Koordinator), kein offener Port.
# Build aus dem Repo-Root:  docker build -f bot/Dockerfile -t tausendfuessler-bot .

# ---- Build: nur common + bot ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
# Der Reaktor liest alle Modul-POMs aus dem Root-pom.xml, gebaut wird aber nur -pl bot -am
COPY worker/pom.xml worker/
COPY coordinator/pom.xml coordinator/
COPY loadtest/pom.xml loadtest/
COPY common common
COPY bot bot
RUN mvn -q -B -pl bot -am -Dmaven.test.skip=true package

# ---- Runtime: nur die JRE und das Fat-Jar, kein Root ----
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 1001 app
USER app
WORKDIR /home/app
COPY --from=build /src/bot/target/bot.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
