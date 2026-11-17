# Room Reservation API

API REST para crear salas, consultar disponibilidad mediante reservas, registrar reservas y cancelarlas. Implementada con Java 21, Spring Boot 4.1.2, Gradle 8.14.3, H2 y Flyway.

## 1. Ejecución

Requiere JDK 21. El repositorio incluye Gradle Wrapper; no requiere Gradle global ni una base de datos externa.

```powershell
# Windows / PowerShell
.\gradlew.bat bootRun
```

```bash
# Linux / macOS
bash ./gradlew bootRun
```

La API escucha en `http://localhost:8080`. H2 persiste datos en `./data/meetingrooms.mv.db`. Flyway administra las migraciones y Hibernate valida el esquema con `ddl-auto: validate`. `open-in-view` está deshabilitado.

Para generar y ejecutar el JAR:

```powershell
.\gradlew.bat clean check bootJar --no-daemon
java -jar build/libs/room-reservation-api-1.0.0.jar
```

En Linux/macOS, sustituir `.\gradlew.bat` por `bash ./gradlew`.

## 2. Autenticación

Los GET son públicos. POST y DELETE requieren HTTP Basic. La configuración es stateless, usa BCrypt para el usuario en memoria y deshabilita CSRF. Las rutas y métodos no declarados están denegados.

Credenciales de demostración local: `admin` / `dev-only-change-me`. Se pueden sustituir mediante variables de entorno:

```powershell
$env:BOOKING_BASIC_USER = 'editor'
$env:BOOKING_BASIC_PASSWORD = '<contraseña>'
.\gradlew.bat bootRun
```

Usar TLS para HTTP Basic fuera de localhost. El servicio no implementa registro de usuarios ni emisión de tokens.

## 3. Contrato REST

| Método | Ruta | Acceso | Respuestas |
|---|---|---|---|
| POST | `/api/rooms` | Autenticado | 201, 400, 401, 409 |
| GET | `/api/rooms` | Público | 200 |
| GET | `/api/rooms/{id}` | Público | 200, 400, 404 |
| POST | `/api/bookings` | Autenticado | 201, 400, 401, 404, 409 |
| GET | `/api/bookings` | Público | 200, 400 |
| GET | `/api/bookings/{id}` | Público | 200, 400, 404 |
| DELETE | `/api/bookings/{id}` | Autenticado | 204, 400, 401, 404 |

Los POST devuelven el recurso creado y una cabecera `Location`.

### Salas

`name` es obligatorio, admite hasta 100 caracteres y se guarda sin espacios en los extremos. La unicidad ignora mayúsculas según `UPPER` de H2. `capacity` admite valores de 1 a 1000; si se omite, se utiliza 1. El listado se ordena por nombre.

```bash
curl -i -u admin:dev-only-change-me -H 'Content-Type: application/json' \
  -d '{"name":"Sala Norte","capacity":10}' http://localhost:8080/api/rooms
```

### Reservas

`roomId` debe ser positivo y corresponder a una sala existente. `responsible` es obligatorio y admite hasta 120 caracteres. Las marcas temporales se reciben como `Instant` ISO-8601, con zona u offset, y se comparan en UTC.

La precisión admitida es de microsegundos: los nanosegundos deben ser múltiplos de 1000. Una precisión mayor devuelve 400; no se truncan ni redondean entradas. Se exige `startAt < endAt`. Se permiten fechas históricas.

```bash
curl -i -u admin:dev-only-change-me -H 'Content-Type: application/json' \
  -d '{"roomId":1,"responsible":"Ana","startAt":"2026-12-01T10:00:00Z","endAt":"2026-12-01T11:00:00Z"}' \
  http://localhost:8080/api/bookings
```

Consulta:

```bash
curl 'http://localhost:8080/api/bookings?roomId=1&date=2026-12-01&page=0&size=20'
```

- `roomId`: filtro opcional por sala.
- `date`: día UTC en formato `YYYY-MM-DD`; incluye reservas que intersectan ese día.
- `page`: página desde 0; valor predeterminado 0.
- `size`: entre 1 y 100; valor predeterminado 20.
- Orden: `startAt ASC, id ASC`.
- Respuesta: `content`, `page`, `size`, `totalElements`, `totalPages`.
- La consulta incluye reservas activas y canceladas.

Cancelación:

```bash
curl -i -X DELETE -u admin:dev-only-change-me http://localhost:8080/api/bookings/1
```

La cancelación cambia el estado a `CANCELLED`, conserva la fila y libera el horario. Repetir la cancelación de una reserva existente devuelve 204.

## 4. Consistencia y concurrencia

Los intervalos son semiabiertos `[startAt, endAt)`. Dos reservas adyacentes no se solapan. El conflicto se comprueba únicamente contra reservas `ACTIVE`:

```text
existing.startAt < requested.endAt && existing.endAt > requested.startAt
```

La creación adquiere un bloqueo `PESSIMISTIC_WRITE` sobre la sala antes de consultar solapamientos. El bloqueo permanece hasta finalizar la transacción. La cancelación mantiene el orden: resolver identificador de sala, bloquear sala, leer reserva y actualizar estado.

Para nombres de sala, V2 incorpora `name_key`, columna calculada con `UPPER(name)`, y una restricción única. La restricción resuelve carreras que el chequeo previo de aplicación no puede impedir. El nombre original se conserva para las respuestas.

## 5. Arquitectura

```text
com.example.roomreservation
├── domain
│   ├── model                 # Room, Booking, BookingStatus
│   └── exception             # Errores de negocio
├── application
│   ├── port/in               # Casos de uso
│   ├── port/out              # Puertos de persistencia
│   └── service               # Validación, orquestación y transacciones
└── infrastructure
    ├── adapter/in/rest       # Controladores, DTOs y errores HTTP
    ├── adapter/out/persistence # JPA, repositorios y mapeo
    └── config                # Spring Security
```

El dominio no depende de Spring ni JPA. La aplicación utiliza Spring para transacciones y Spring Data para paginación. Los adaptadores convierten modelos de dominio y entidades JPA mediante mapeo explícito. Las validaciones de entradas de los casos de uso se ejecutan antes de acceder a persistencia.

H2 permite ejecución local sin servicios externos. Flyway mantiene el esquema versionado. El bloqueo por sala protege también la primera reserva, cuando todavía no existen filas de reservas que bloquear. La defensa de estas decisiones se encuentra en `docs/DECISIONES.md`.

## 6. Migraciones

- V1: tablas `rooms` y `bookings`, claves, restricciones e índices.
- V2: clave calculada y unicidad de nombres insensible a mayúsculas.

V1 se conserva sin modificaciones. Antes de actualizar una base existente a V2, identificar duplicados:

```sql
SELECT UPPER(name), COUNT(*)
FROM rooms
GROUP BY UPPER(name)
HAVING COUNT(*) > 1;
```

Si existen duplicados, deben resolverse antes de aplicar V2. La migración no elimina ni renombra datos automáticamente. Realizar una copia de la base antes de actualizarla. H2 no revierte necesariamente todo el DDL de una migración fallida; si V2 falla, revisar el esquema y el historial Flyway antes de reintentar. No ejecutar reparaciones automáticas sobre datos existentes.

## 7. Errores

Los errores de aplicación 400, 404 y 409 utilizan `application/problem+json` conforme a RFC 9457. Los errores de campos incluyen `violations` con `field` y `message`.

- 400: entrada inválida, identificador no positivo, intervalo inválido o precisión temporal no admitida.
- 404: sala o reserva inexistente.
- 409: solapamiento o conflicto de unicidad.
- 401/403: respuestas de Spring Security; no utilizan necesariamente Problem Details.

## 8. Pruebas y cobertura

```powershell
.\gradlew.bat clean check bootJar --no-daemon
```

Las pruebas usan H2 en memoria con migraciones Flyway. La suite incluye seguridad, reglas de reserva, filtros UTC, paginación, cancelación, entradas directas a casos de uso y concurrencia con hilos y transacciones independientes.

JaCoCo genera HTML en `build/reports/jacoco/test/html/index.html` y XML en `build/reports/jacoco/test/jacocoTestReport.xml`. `check` exige al menos 80% en instrucciones y ramas, sin excluir clases. Los resultados JUnit se encuentran en `build/test-results/test/`.

Resultado local del 2026-11-17: **38 pruebas, 0 fallos y 0 errores**; cobertura de instrucciones **95,07%**, líneas **95,17%** y ramas **100%**. Se verificó la migración desde V1 con datos existentes y el rechazo de duplicados tanto en la API concurrente como directamente en la base de datos.

## 9. Integración continua

`.github/workflows/ci.yml` ejecuta `bash ./gradlew clean check bootJar --no-daemon` en Ubuntu y Temurin 21 ante pushes, pull requests o ejecución manual. Publica reportes durante 14 días, incluso ante errores, y el JAR solo si la verificación finaliza correctamente. No realiza despliegues.

El workflow está preparado. Su ejecución remota está pendiente; el repositorio local no tiene remoto configurado en la última revisión.

## 10. Alcance y evolución

La validación corresponde a Java 21 y H2 local. No incluye PostgreSQL, pruebas de carga sostenida ni despliegues distribuidos. La estrategia requiere que todas las escrituras de reservas respeten el bloqueo de sala.

Posibles ampliaciones: PostgreSQL con Testcontainers y restricción de exclusión sobre `tstzrange`, identidad OIDC, métricas y trazas, políticas horarias por sala y medición de consultas para evaluar N+1 en listados.

Tiempo efectivo total: no medido.

## 11. Uso de inteligencia artificial

Se utilizaron Antigravity en la preparación inicial, según el registro del proyecto, y Codex en la implementación de seguridad, ampliación de pruebas, cobertura, correcciones de consistencia, CI y documentación.

- **Skills propias:** se aplicaron instrucciones reutilizables del equipo para Spring Security, persistencia JPA, pruebas unitarias e integración y cobertura JaCoCo. Estas instrucciones guiaron la revisión de convenciones, límites transaccionales, escenarios de prueba y validación. Se prepararon copias locales para OpenCode; su carga en una sesión de OpenCode aún no está verificada.
- **Plan de implementación:** el trabajo se organizó por fases de configuración, salas, reservas, consultas/cancelación, seguridad, pruebas y entrega. El estado se contrastó con los commits y el árbol de trabajo antes de continuar. Los commits fueron realizados por el desarrollador.
- **Revisión y comparación:** se compararon contratos REST, esquema SQL, validaciones y documentación con el código existente. Las alternativas de persistencia y bloqueo se evaluaron considerando el alcance local. Las correcciones se contrastaron mediante pruebas automatizadas, reportes de cobertura y comprobaciones de ejecución; también se consultó documentación oficial de H2, Spring Security, OpenCode y GitHub Actions.
- **Multiagente:** no se ejecutó una revisión con subagentes independientes en las sesiones de Codex registradas. El uso de distintas herramientas a lo largo del desarrollo no constituye una validación multiagente.
- **Validación:** las afirmaciones de funcionamiento se apoyan en comandos ejecutados y sus resultados. La generación o revisión asistida no sustituye la revisión del desarrollador ni acredita una ejecución remota del pipeline.
