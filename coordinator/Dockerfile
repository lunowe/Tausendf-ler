# Koordinator: REST-API (8080) + Worker-TCP-Server (9090).
# Build aus dem Repo-Root:  docker build -f coordinator/Dockerfile -t tausendfuessler-coordinator .

# ---- Build: nur die benoetigten Module (common, worker als Test-Abhaengigkeit, coordinator) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
# Der Reaktor liest alle Modul-POMs aus dem Root-pom.xml, gebaut wird aber nur -pl coordinator -am
COPY bot/pom.xml bot/
COPY loadtest/pom.xml loadtest/
COPY common common
COPY worker worker
COPY coordinator coordinator
RUN mvn -q -B -pl coordinator -am -Dmaven.test.skip=true package

# ---- Runtime: nur die JRE und das Fat-Jar, kein Root ----
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 1001 app
USER app
WORKDIR /home/app
COPY --from=build /src/coordinator/target/coordinator.jar app.jar
# Logdatei innerhalb des Containers (siehe application.yml, logging.file.name)
ENV LOG_FILE=/home/app/logs/coordinator.log
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
