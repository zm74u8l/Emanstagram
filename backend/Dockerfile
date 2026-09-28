# ---- build ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Dependencies first, so a code-only change reuses this cached layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- run -----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/emanstagram-backend-*.jar app.jar
USER app
# Configuration comes from environment variables set on the host
# (DATABASE_URL, SUPABASE_SECRET_KEY, JWT_SECRET, CORS_ORIGINS, ...).
# The host's $PORT is picked up by application.yml.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
