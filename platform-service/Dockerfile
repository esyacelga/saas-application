# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

# Descarga dependencias primero (capa cacheada mientras pom.xml no cambie)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Compila y empaqueta sin ejecutar tests
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl

# Usuario no-root para mejor seguridad
RUN addgroup -S gymadmin && adduser -S gymadmin -G gymadmin

COPY --from=builder /app/target/platform-service-new.jar app.jar

USER gymadmin

# Puerto en que escucha el servicio (sobreescribe la propiedad en application.yml)
EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
