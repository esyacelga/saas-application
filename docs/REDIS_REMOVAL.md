# Redis — Eliminado temporalmente

**Fecha:** 2026-06-19  
**Motivo:** Despliegue inicial en Cloud Run + Neon.com sin dependencias externas de caché.  
**Impacto:** Solo `platform-service`. El check de módulos va directo a PostgreSQL en cada request.

---

## Qué se cambió

### platform-service

| Archivo | Cambio |
|---------|--------|
| `pom.xml` | Eliminadas dependencias `spring-boot-starter-data-redis-reactive` y `spring-boot-starter-cache` |
| `application.yml` | Eliminada sección `spring.data.redis` y propiedad `module-check.cache-ttl-seconds` |
| `application-test.yml` | Ídem |
| `infrastructure/config/RedisConfig.java` | Vaciada (conservada para no romper el árbol de paquetes) |
| `infrastructure/adapter/out/cache/RedisModuloCheckCache.java` | Reemplazada por implementación no-op: `get` retorna `Mono.empty()`, `put` y `evict` retornan `Mono.empty()` |
| `application/service/ModuloCheckService.java` | Eliminado campo `cacheTtl` y `@Value("${module-check.cache-ttl-seconds}")`. TTL hardcodeado en `Duration.ofSeconds(300)` para cuando se restaure. |

### core-service

| Archivo | Cambio |
|---------|--------|
| `pom.xml` | Eliminadas dependencias `spring-boot-starter-data-redis-reactive` y `spring-boot-starter-cache` |
| `application.yml` | Eliminada sección `spring.data.redis` |
| `application-test.yml` | Ídem |

---

## Cómo restaurar Redis (cuando la app crezca)

### 1. Proveedor recomendado: Upstash Redis

Upstash es serverless, compatible con Lettuce (el cliente por defecto de Spring), y tiene free tier.  
Crear instancia en upstash.com → copiar la URL de conexión tipo: `redis://default:<password>@<host>:<port>`

### 2. Restaurar dependencias en `platform-service/pom.xml`

Agregar dentro de `<dependencies>`:

```xml
<!-- Spring Data Redis Reactive -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>

<!-- Spring Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### 3. Restaurar `application.yml` en platform-service

Agregar bajo `spring:`:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

Y agregar al nivel raíz:

```yaml
module-check:
  cache-ttl-seconds: ${MODULE_CHECK_CACHE_TTL_SECONDS:300}
```

### 4. Restaurar `RedisConfig.java`

Reemplazar el contenido de `infrastructure/config/RedisConfig.java`:

```java
package com.gymadmin.platform.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gymadmin.platform.domain.model.ModuloCheckResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, ModuloCheckResult> moduloCheckRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<ModuloCheckResult> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ModuloCheckResult.class);

        RedisSerializationContext<String, ModuloCheckResult> context =
                RedisSerializationContext.<String, ModuloCheckResult>newSerializationContext(
                                new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
```

### 5. Restaurar `RedisModuloCheckCache.java`

Reemplazar la implementación no-op con la real:

```java
package com.gymadmin.platform.infrastructure.adapter.out.cache;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import com.gymadmin.platform.domain.port.out.ModuloCheckCache;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RedisModuloCheckCache implements ModuloCheckCache {

    private static final String KEY_PREFIX = "modulo:check:";

    private final ReactiveRedisTemplate<String, ModuloCheckResult> redisTemplate;

    public RedisModuloCheckCache(ReactiveRedisTemplate<String, ModuloCheckResult> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<ModuloCheckResult> get(Long idCompania, String codigo) {
        return redisTemplate.opsForValue().get(buildKey(idCompania, codigo))
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Void> put(Long idCompania, String codigo, ModuloCheckResult result, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(buildKey(idCompania, codigo), result, ttl)
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Void> evict(Long idCompania) {
        String pattern = KEY_PREFIX + idCompania + ":*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    private String buildKey(Long idCompania, String codigo) {
        return KEY_PREFIX + idCompania + ":" + codigo;
    }
}
```

### 6. Restaurar `ModuloCheckService.java`

Agregar el import `@Value` y el campo `cacheTtl`:

```java
import org.springframework.beans.factory.annotation.Value;
// ...
private final Duration cacheTtl;

// En el constructor, agregar parámetro:
@Value("${module-check.cache-ttl-seconds:300}") long cacheTtlSeconds
this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);

// En checkAcceso, cambiar:
cache.put(idCompania, codigo, result, Duration.ofSeconds(300))
// por:
cache.put(idCompania, codigo, result, cacheTtl)
```

### 7. Variables de entorno en Cloud Run

Agregar al servicio `platform-service`:
```
REDIS_HOST=<upstash-host>
REDIS_PORT=<upstash-port>
MODULE_CHECK_CACHE_TTL_SECONDS=300
```

### 8. Cuándo tiene sentido restaurarlo

Considera reactivar Redis cuando:
- El endpoint `/api/v1/modulos/check` reciba más de ~100 req/min sostenidos
- El costo de queries a Neon empiece a ser significativo
- Notes latencia alta en validaciones de módulos (el check hace un JOIN en `saas.plan_caracteristicas`)
