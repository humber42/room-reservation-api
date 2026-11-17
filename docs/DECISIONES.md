# Defensa técnica - preguntas para la reunión de 30 minutos

**¿Por qué H2 y no PostgreSQL?** Ambos son válidos para la consigna; H2 elimina una dependencia operativa y permite ejecutar la prueba rápidamente. Se usa H2 persistente en la app y en memoria en tests, ambos con Flyway. Para un despliegue productivo migraría a PostgreSQL y Testcontainers; no presupongo compatibilidad SQL total sin validación.

**¿Por qué Spring Boot 4.1.2?** Es la versión fijada en el proyecto y validada localmente con Java 21 y Gradle 8.14.3. No se modificaron las versiones del stack durante estas correcciones.

**¿Por qué HTTP Basic y no JWT?** La prueba permite ambos. Con un único editor de prueba, Basic + TLS ofrece protección de escritura sin construir endpoints de login, firma, refresh token ni servidor de autorización. En una solución real usaría un proveedor de identidad y permisos por usuario.

**¿Por qué bloqueo pesimista?** El flujo read-check-insert no es seguro bajo concurrencia: dos peticiones podrían comprobar la disponibilidad al mismo tiempo. La transacción bloquea la sala antes del chequeo. El lock se mantiene hasta el commit; en concurrencia sobre la misma sala, una petición espera y después vuelve a consultar.

**¿Por qué bloquear la sala y no las reservas existentes?** Cuando la sala aún no tiene reservas no existe ninguna fila de reserva para bloquear. El registro de la sala sirve como punto estable de serialización de operaciones por recurso, con menos complejidad que gestionar rangos o exclusiones de base de datos.

**¿Es suficiente en varias instancias?** Sí para todas las instancias que comparten la misma base de datos transaccional y aplican el mismo protocolo de bloqueo de sala. No protege escrituras directas que omitan ese protocolo. En PostgreSQL podría añadirse una constraint EXCLUDE con rango temporal para una garantía aún más fuerte.

**¿Por qué no validar que la reserva sea futura?** El enunciado no lo exige. Restringir reservas históricas impediría ciertas cargas o pruebas. Es una política de negocio para definir explícitamente.

**¿Por qué usar intervalos [inicio, fin)?** Permite que una reunión termine a las 11:00 y otra comience exactamente a las 11:00 sin considerarlas solapadas. La condición de solapamiento se mantiene sencilla y coherente.

**¿Por qué Instant y UTC?** Evita conflictos de interpretación entre clientes ubicados en husos horarios diferentes y cambios de horario de verano. Para mostrar horas locales, el cliente convierte el valor según su zona.

**¿Qué significa filtrar por fecha?** El día civil UTC, desde 00:00 inclusivo hasta el día siguiente exclusivo, incluyendo cualquier reserva que intersecte ese intervalo. Se documenta para evitar ambigüedad.

**¿Por qué cancelación lógica?** Conservar el registro facilita inspección y auditoría. La comprobación de disponibilidad solo considera ACTIVE. El mismo DELETE de una reserva existente puede ejecutarse repetidamente con 204.

**¿Por qué devolver Problem Details?** Unifica errores, mantiene códigos semánticos y permite enviar detalles de campos inválidos sin exponer stack traces internos.

**¿Por qué no microservicios/eventos?** El dominio tiene dos recursos y un único límite transaccional local. Separarlo introduciría complejidad distribuida sin necesidad clara.

**¿Cómo probar concurrencia?** La suite incluye una prueba con dos hilos sincronizados mediante latches y peticiones MockMvc en transacciones independientes. Verifica exactamente un 201, un 409 y una sola reserva persistida en H2. Para demostrarlo en producción usaría PostgreSQL con Testcontainers.

**Limitaciones deliberadas:** no hay gestión multiusuario, roles finos, timezone local configurable, políticas horarias por sala, rate limits ni métricas; están fuera de la consigna.


**¿Cómo se preserva la precisión temporal?** La aplicación rechaza valores con precisión superior a microsegundos mediante BadRequestException (400 por REST), antes de consultar disponibilidad. No redondea ni trunca los instantes. El esquema TIMESTAMP(6) puede representar exactamente los valores admitidos.

**¿Cómo se evita duplicar salas con distinta capitalización?** V2 agrega una columna generada UPPER(name) y una restricción única. El chequeo previo facilita el error de negocio; la restricción protege las inserciones concurrentes. Una base anterior con duplicados debe corregirse antes de migrar; no se eliminan datos automáticamente.
