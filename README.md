# Room Reservation API

API REST empresarial para la gestión y reserva de salas de reuniones, desarrollada con **Java 21**, **Spring Boot 4.1.2** y **Gradle 8.14.3**.

---

## 1. Requisitos previos

- **JDK 21 LTS** instalado (verificar con `java -version`).
- No se requiere la instalación de bases de datos externas ni Docker; la aplicación utiliza **H2** embebido con persistencia en archivo y esquema gestionado por **Flyway**.
- El proyecto incluye el wrapper oficial de Gradle (`gradlew` y `gradlew.bat`), por lo que no es necesario instalar Gradle previamente.

---

## 2. Cómo arrancar el proyecto

### 2.1 Ejecución en entorno local

Desde la raíz del proyecto:

En Linux / macOS:
```bash
./gradlew bootRun
```

En Windows (PowerShell / CMD):
```powershell
.\gradlew.bat bootRun
```

La API quedará disponible en `http://localhost:8080`.

Al arrancar:
1. **Flyway** crea y valida automáticamente el esquema relacional (`rooms` y `bookings`) a partir de las migraciones SQL.
2. La base de datos H2 se persiste localmente en `./data/meetingrooms.mv.db` (este directorio está excluido en `.gitignore` para no versionar datos de ejecución).
3. Hibernate está configurado con `ddl-auto: validate` para garantizar que la estructura física de la base de datos pertenece exclusivamente a Flyway.

### 2.2 Credenciales de autenticación (HTTP Basic)

Las operaciones de modificación (`POST` y `DELETE`) están protegidas mediante HTTP Basic. Para desarrollo local se incluyen credenciales por defecto:

- **Usuario:** `admin`
- **Contraseña:** `dev-only-change-me`

Para configurar credenciales personalizadas mediante variables de entorno:

En Linux / macOS:
```bash
export BOOKING_BASIC_USER=mi_usuario
export BOOKING_BASIC_PASSWORD=mi_clave_segura
./gradlew bootRun
```

En Windows (PowerShell):
```powershell
$env:BOOKING_BASIC_USER = 'mi_usuario'
$env:BOOKING_BASIC_PASSWORD = 'mi_clave_segura'
.\gradlew.bat bootRun
```

---

## 3. Endpoints de la API REST

| Método | Ruta | Autenticación | Códigos HTTP | Descripción |
|---|---|---|---|---|
| `POST` | `/api/rooms` | HTTP Basic | `201`, `400`, `401`, `409` | Crea una sala. Devuelve header `Location` y el recurso creado. |
| `GET` | `/api/rooms` | Pública | `200` | Lista todas las salas ordenadas alfabéticamente por nombre. |
| `GET` | `/api/rooms/{id}` | Pública | `200`, `404` | Obtiene el detalle de una sala por ID. |
| `POST` | `/api/bookings` | HTTP Basic | `201`, `400`, `401`, `404`, `409` | Crea una reserva de sala. Valida solapamiento bajo bloqueo pesimista. |
| `GET` | `/api/bookings` | Pública | `200`, `400` | Consulta paginada y filtrable por `roomId` y `date` (UTC). |
| `GET` | `/api/bookings/{id}` | Pública | `200`, `404` | Obtiene el detalle de una reserva por ID. |
| `DELETE` | `/api/bookings/{id}` | HTTP Basic | `204`, `401`, `404` | Cancela lógicamente una reserva (idempotente). |

---

## 4. Ejemplos de uso con cURL

### 4.1 Crear una sala
El campo `name` es obligatorio y único; `capacity` es opcional (valor predeterminado: `1`).

```bash
curl -i -X POST http://localhost:8080/api/rooms \
  -u admin:dev-only-change-me \
  -H "Content-Type: application/json" \
  -d '{"name": "Sala Innovación", "capacity": 10}'
```
*Respuesta:* `201 Created` con cabecera `Location: /api/rooms/1`.

### 4.2 Listar salas (Público)
```bash
curl -i http://localhost:8080/api/rooms
```

### 4.3 Crear una reserva
Las marcas de tiempo deben enviarse en formato ISO-8601 con zona horaria o sufijo `Z` (UTC).

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -u admin:dev-only-change-me \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": 1,
    "responsible": "Carlos Pérez",
    "startAt": "2026-12-01T09:00:00Z",
    "endAt": "2026-12-01T10:30:00Z"
  }'
```
*Respuesta:* `201 Created` con cabecera `Location: /api/bookings/1`.

### 4.4 Intento de reserva solapada (Conflicto 409)
Si se intenta reservar la misma sala en un horario que se cruce con una reserva activa:

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -u admin:dev-only-change-me \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": 1,
    "responsible": "Lucía Gómez",
    "startAt": "2026-12-01T10:00:00Z",
    "endAt": "2026-12-01T11:00:00Z"
  }'
```
*Respuesta:* `409 Conflict` (Problem Details explicando que el horario ya está ocupado).

### 4.5 Consultar reservas con filtros y paginación (Público)
Parámetros opcionales soportados:
- `roomId`: ID de la sala.
- `date`: Día civil UTC en formato `YYYY-MM-DD` (incluye reservas que intersecten ese día).
- `page`: Número de página (base 0, por defecto: `0`).
- `size`: Tamaño de página (entre `1` y `100`, por defecto: `20`).

```bash
curl -i "http://localhost:8080/api/bookings?roomId=1&date=2026-12-01&page=0&size=10"
```

### 4.6 Cancelar una reserva
```bash
curl -i -X DELETE http://localhost:8080/api/bookings/1 \
  -u admin:dev-only-change-me
```
*Respuesta:* `204 No Content`. La llamada es **idempotente**: repetir el `DELETE` sobre la misma reserva cancelada vuelve a responder `204 No Content`.

---

## 5. Reglas de negocio y concurrencia

1. **Intervalos semiabiertos $[startAt, endAt)$:**  
   El instante final es exclusivo. Si una reserva finaliza a las `10:00:00Z`, otra reserva en la misma sala puede iniciar exactamente a las `10:00:00Z` sin considerarse solapada.
2. **Detección de solapamiento:**  
   Dos reservas activas para la misma sala se intersectan si y solo si:  
   `existing.startAt < requested.endAt && existing.endAt > requested.startAt`.
3. **Bloqueo pesimista sobre la sala (`PESSIMISTIC_WRITE`):**  
   Para evitar la condición de carrera *Time-of-Check to Time-of-Use* (dos hilos concurrentes que comprueban disponibilidad simultáneamente y ambos insertan), la transacción adquiere primero un bloqueo pesimista sobre la fila de la sala (`SELECT ... FOR UPDATE` en `Room`). El bloqueo serializa las operaciones competidoras sobre la misma sala física hasta el `commit` de la transacción.
4. **Prevención de interbloqueos (*deadlocks*):**  
   Tanto en la creación como en la cancelación, se sigue estrictamente el **mismo orden de bloqueo**:
   - En `BookingService.create`: se bloquea primero `Room` y luego se inserta `Booking`.
   - En `BookingService.cancel`: primero se obtiene el ID de la sala sin lock, luego se adquiere el bloqueo sobre `Room`, y finalmente se actualiza el estado de `Booking`.
5. **Cancelación lógica:**  
   La cancelación no elimina físicamente la fila, sino que actualiza su estado a `CANCELLED`, liberando el horario para futuras reservas y conservando la trazabilidad de auditoría.

---

## 6. Decisiones técnicas y justificación

| Decisión | Justificación técnica | Compromisos y alternativas consideradas |
|---|---|---|
| **Java 21 LTS** | Estándar de la industria con soporte a largo plazo. Permite aprovechar *records* inmutables para DTOs, *pattern matching* y optimizaciones del runtime. | Cumple sobradamente el requisito de Java 17+. |
| **Spring Boot 4.1.2** | Versión moderna y estable con soporte activo para Java 21, evitando líneas legacy cuyo soporte comunitario ha finalizado. | Requiere Gradle 8.14+ y configuración modularizada `webmvc`. |
| **Gradle 8.14.3** | Garantiza compilación reproducible y portabilidad absoluta tanto en desarrollo local como en el pipeline de CI. | Wrapper versionado en el repositorio para no depender de instalaciones globales. |
| **H2 con persistencia en archivo** | Elimina la dependencia operativa de desplegar contenedores externos para ejecutar y evaluar la prueba rápidamente. Persistir en archivo (`meetingrooms.mv.db`) permite conservar datos entre reinicios. | Para un entorno productivo de alta concurrencia se migraría a PostgreSQL con Testcontainers. |
| **Flyway para migraciones** | Esquema gobernado por código versionado en SQL (`V1__create_rooms_and_bookings.sql`). Al combinarlo con `ddl-auto: validate`, se prohíbe que Hibernate altere tablas en caliente. | Elimina discrepancias entre entornos y errores de generación automática de DDL. |
| **HTTP Basic stateless** | Cumple con la exigencia de proteger endpoints de mutación con la menor sobrecarga operativa y cognitiva. No requiere construir endpoints de login, almacenamiento de refresh tokens ni servidores de identidad. | En producción se utilizaría un proveedor de identidad (OIDC/Keycloak/Auth0) con tokens JWT y roles granulares. |
| **Bloqueo pesimista a nivel de Sala** | Bloquear la fila de la sala garantiza exclusión mutua transaccional incluso si la sala aún no tiene ninguna reserva previa registrada (caso donde no existen filas de reserva para bloquear). | Evita mecanismos en memoria (`synchronized`) que fallarían al escalar a múltiples instancias de la aplicación. |
| **`Instant` (UTC)** | Almacena y calcula marcas temporales absolutas independientes de la zona horaria del servidor o del cliente, previniendo discrepancias por horario de verano. | La conversión a zona local queda como responsabilidad de la interfaz cliente. |
| **Problem Details (RFC 9457)** | Estandariza las respuestas de error en formato `application/problem+json`, estructurando detalles de validación de campos sin exponer stack traces internos. | Proporciona una experiencia de depuración profesional y predecible a los consumidores de la API. |

---

## 7. Formato de errores (RFC 9457 - Problem Details)

Todos los errores controlados devuelven `application/problem+json`:

- **HTTP 400 (Bad Request):** JSON mal formado, campos obligatorios omitidos, intervalo `startAt >= endAt` o parámetros de paginación inválidos.
  ```json
  {
    "type": "about:blank",
    "title": "Validation error",
    "status": 400,
    "detail": "One or more request fields are invalid",
    "violations": [
      {"field": "name", "message": "name is required"}
    ]
  }
  ```
- **HTTP 401 (Unauthorized):** Petición de escritura (`POST` o `DELETE`) sin credenciales o con credenciales erróneas.
- **HTTP 404 (Not Found):** Sala o reserva inexistente.
- **HTTP 409 (Conflict):** Solapamiento de reserva con una existente o intento de duplicar el nombre de una sala.

---

## 8. Pruebas automatizadas

Para ejecutar la suite completa de pruebas:

En Linux / macOS:
```bash
./gradlew clean test
```

En Windows:
```powershell
.\gradlew.bat clean test
```

Las pruebas utilizan el perfil `test` (`application-test.yml`), levantando una base de datos H2 en memoria migrada automáticamente por Flyway.

### Casos de prueba cubiertos
- **Pruebas de integración (`MockMvc` + `@SpringBootTest`):**
  - Creación y listado público de salas (`201` con header `Location`, `200`).
  - Capacidad predeterminada a 1 cuando se omite.
  - Rechazo de nombres de sala duplicados (`409 Conflict`).
  - Detección y bloqueo de reservas solapadas (`409 Conflict`).
  - Aceptación de reservas contiguas (adyacentes exactas `10:00-11:00` y `11:00-12:00`).
  - Cancelación lógica, idempotencia (`204`) y liberación inmediata del horario.
  - Paginación y ordenamiento estable (`startAt ASC, id ASC`).
  - Filtro por fecha UTC que intersecta el día civil (incluso si cruza medianoche).
  - Protección perimetral de seguridad: rechazo `401 Unauthorized` ante escrituras sin credenciales y acceso público garantizado en lecturas `GET`.
  - Validación de intervalos temporales inválidos (`400 Bad Request`).
- **Pruebas unitarias de negocio (`JUnit 5` + `Mockito`):**
  - Validación previa a base de datos de intervalo `startAt >= endAt`.
  - Validación previa a base de datos de rangos de paginación (`size < 1` o `page < 0`).

---

## 9. Qué mejoraría con más tiempo

1. **Migración a PostgreSQL y Testcontainers:** Reemplazar H2 en las pruebas de integración por contenedores reales de PostgreSQL mediante Testcontainers, implementando una restricción de exclusión a nivel de motor relacional (`EXCLUDE USING gist (room_id WITH =, tsrange(start_at, end_at) WITH &&) WHERE (status = 'ACTIVE')`).
2. **Gestión de identidades y roles:** Migrar de HTTP Basic a OAuth2/OIDC con JWT (roles de Administrador, Gestor de Sala y Usuario final).
3. **Observabilidad:** Integrar Spring Boot Actuator, Micrometer y OpenTelemetry para métricas de latencia de endpoints, conteo de conflictos de reserva y trazas distribuidas.
4. **Políticas horarias:** Añadir configuración por sala de horario hábil permitido (ej. 08:00 a 20:00) y duración mínima/máxima de reserva.

---

## 10. Uso de herramientas de inteligencia artificial

En cumplimiento con las directrices de evaluación de la prueba técnica, se documenta de forma transparente el uso de herramientas de IA durante el desarrollo:

- **Herramientas utilizadas:** Asistente de IA (Antigravity) para pair programming y aceleración de tareas de scaffolding.
- **Intervenciones realizadas:**
  - Análisis inicial de compatibilidad de dependencias (detección del requisito de Gradle 8.14+ para Spring Boot 4.1.2).
  - Generación del esqueleto de migraciones Flyway y configuración de Bean Validation.
  - Contraste formal de la condición matemática de solapamiento de intervalos semiabiertos.
  - Estructuración de la suite de pruebas de integración con MockMvc.
- **Validaciones efectuadas por el desarrollador:**
  - Revisión y ajuste manual de la estrategia de bloqueo pesimista (`findByIdForUpdate`) para evitar deadlocks en la cancelación.
  - Verificación del comportamiento del driver H2 con marcas de tiempo UTC.
  - Ejecución real de compilaciones y pruebas automatizadas (`./gradlew clean test`).
