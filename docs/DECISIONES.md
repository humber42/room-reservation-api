# Decisiones técnicas

Este documento explica las decisiones reflejadas en la implementación, el problema que resuelven y sus consecuencias. El alcance es una API de reservas con ejecución local sobre H2. Las alternativas mencionadas no están implementadas, salvo indicación expresa.

## 1. Un servicio con puertos y adaptadores

**Decisión.** Mantener salas y reservas en una sola aplicación, separando dominio, casos de uso y adaptadores REST/JPA.

**Justificación.** La reserva necesita consultar la sala, comprobar solapamientos y guardar el resultado dentro de una transacción. Un servicio permite mantener ese límite transaccional sin coordinación distribuida. Los puertos separan los casos de uso de la persistencia concreta y permiten probar su comportamiento con dobles de los repositorios.

**Consecuencias.** El dominio no depende de Spring ni JPA y los adaptadores realizan mapeo explícito. Esa separación añade interfaces y conversiones. La aplicación conserva dependencias de Spring para transacciones y de Spring Data para paginación; no se presenta como un núcleo completamente independiente de frameworks. Separar microservicios requeriría una necesidad de despliegue o autonomía que el alcance actual no tiene.

## 2. Java 21, Spring Boot y Gradle Wrapper

**Decisión.** Usar Java 21, Spring Boot 4.1.2 y Gradle Wrapper 8.14.3, con versiones declaradas en el proyecto.

**Justificación.** Java 21 fija el nivel de lenguaje y la toolchain de compilación. Spring Boot integra MVC, validación, seguridad, JPA y Flyway, evitando configurar cada integración por separado. El wrapper establece la versión de Gradle utilizada localmente y en CI sin exigir una instalación global. Se mantiene la versión de Spring Boot ya integrada y comprobada con este conjunto de dependencias.

**Consecuencias.** La compatibilidad se verifica mediante compilación y pruebas con Java 21. No se afirma que 4.1.2 sea la única versión adecuada ni que se haya realizado una comparación exhaustiva de versiones. Las actualizaciones requieren ejecutar nuevamente las pruebas; fijar versiones no garantiza por sí solo que dos compilaciones produzcan artefactos idénticos.

## 3. H2 para la ejecución local

**Decisión.** Usar H2 persistente en archivo para la aplicación y H2 en memoria para las pruebas.

**Justificación.** El objetivo es ejecutar la API sin instalar un servidor de base de datos ni Docker. El archivo conserva información entre reinicios; la modalidad en memoria permite preparar datos de prueba sin afectar la base local. Usar el mismo motor en ambos casos permite comprobar las consultas y migraciones utilizadas por la aplicación.

**Consecuencias.** La validación se limita a H2. No demuestra el comportamiento de PostgreSQL, una base compartida por varias instancias ni un entorno con carga sostenida. Si cambian esos requisitos, habría que evaluar otro motor y probarlo directamente, por ejemplo con Testcontainers; no basta con cambiar la URL JDBC.

## 4. Flyway como responsable del esquema

**Decisión.** Versionar los cambios de esquema con Flyway y configurar Hibernate con `ddl-auto: validate`.

**Justificación.** Las restricciones e índices deben evolucionar mediante cambios explícitos y revisables. Hibernate comprueba la correspondencia con las entidades sin crear ni alterar tablas automáticamente. V1 define el esquema inicial y V2 incorpora la unicidad normalizada de nombres sin modificar V1.

**Consecuencias.** Una base existente debe cumplir las condiciones de cada nueva migración. V2 falla si ya hay nombres equivalentes por capitalización: no los elimina ni renombra. H2 puede conservar DDL parcial tras un fallo; se requiere copia previa y revisión del esquema e historial antes de reintentar. Las pruebas cubren tanto la actualización con datos válidos como el rechazo ante duplicados.

## 5. Bloqueo pesimista por sala

**Decisión.** Adquirir `PESSIMISTIC_WRITE` sobre la sala antes de comprobar solapamientos y mantenerlo hasta finalizar la transacción de creación.

**Justificación.** Consultar disponibilidad y luego insertar, sin sincronización, permite que dos peticiones observen el mismo horario libre. La fila de la sala existe incluso cuando todavía no hay reservas, por lo que ofrece un recurso estable que bloquear. Bloquear solamente reservas existentes no cubre ese primer registro. Un bloqueo Java en memoria tampoco coordinaría procesos distintos.

**Consecuencias.** Las operaciones sobre la misma sala se serializan y pueden esperar o fallar por contención. Todas las escrituras de reservas deben respetar este protocolo; no impide inserciones SQL directas que lo omitan. La prueba concurrente verifica un 201, un 409 y una sola reserva persistida en H2. No acredita una configuración de varias instancias. Una restricción de exclusión temporal en PostgreSQL sería una alternativa adicional si se adopta ese motor.

## 6. Orden de bloqueo común para crear y cancelar

**Decisión.** En la cancelación, resolver el identificador de sala, bloquear la sala y después leer y actualizar la reserva.

**Justificación.** Crear y cancelar afectan a la disponibilidad del mismo recurso. Mantener el orden de acceso evita que un flujo bloquee primero una reserva y otro primero la sala, reduciendo el riesgo de dependencias circulares entre esas operaciones.

**Consecuencias.** La cancelación también puede esperar a que termine otra operación sobre la sala. La consulta inicial obtiene el identificador sin bloquear la reserva; el modelo actual no permite trasladar una reserva a otra sala. Si se añade esa operación, habrá que revisar el protocolo. El orden reduce riesgos concretos, pero no garantiza la ausencia de todo deadlock en futuras operaciones.

## 7. Intervalos semiabiertos y comparación en UTC

**Decisión.** Representar los horarios mediante `Instant` y tratar cada reserva como `[startAt, endAt)`.

**Justificación.** Los instantes permiten comparar horarios absolutos recibidos con distintos offsets. El extremo final exclusivo permite reuniones consecutivas: una termina a las 11:00 y otra empieza a las 11:00. El solapamiento se expresa con una única regla sobre reservas activas:

```text
existing.startAt < requested.endAt && existing.endAt > requested.startAt
```

**Consecuencias.** Se exige inicio anterior al fin. El filtro `date` representa el día en fecha UTC y devuelve reservas que lo intersectan, incluidas las que cruzan medianoche. La presentación en una zona local corresponde al cliente. No hay política de horarios laborales ni de fechas futuras: admitir reservas históricas evita introducir una restricción temporal que el contrato actual no define.

## 8. Precisión máxima de microsegundos

**Decisión.** Rechazar instantes cuyos nanosegundos no sean múltiplos de 1000, en lugar de redondearlos o truncarlos.

**Justificación.** `Instant` admite nanosegundos, mientras que las columnas `TIMESTAMP(6)` almacenan microsegundos. Un redondeo al persistir podría cambiar un límite usado para comprobar solapamientos o convertir un intervalo corto en uno vacío. Validar antes de consultar disponibilidad mantiene iguales los valores comprobados y los almacenados.

**Consecuencias.** La API devuelve 400 cuando la precisión supera la admitida. Los clientes deben enviar instantes representables en microsegundos. Se conservan exactamente esos valores, incluida la adyacencia de intervalos de un microsegundo. Admitir nanosegundos exigiría revisar el esquema y la compatibilidad del motor.

## 9. Unicidad de nombres garantizada en la base de datos

**Decisión.** Conservar el nombre visible y añadir en V2 una columna calculada `UPPER(name)` con restricción única.

**Justificación.** El chequeo previo `existsByNameIgnoreCase` permite informar del conflicto, pero dos transacciones podrían superarlo antes de guardar. La restricción hace que la base de datos rechace una de las inserciones equivalentes. Calcular la clave en la base evita que un escritor tenga que proporcionar y mantener una segunda representación del nombre.

**Consecuencias.** La equivalencia sigue la conversión `UPPER` de H2; no es una normalización universal de acentos o reglas lingüísticas. Las respuestas conservan la capitalización del nombre original. Los conflictos se traducen a 409 por REST. La restricción requiere resolver duplicados anteriores antes de actualizar. Se verifica mediante peticiones concurrentes y también con inserciones SQL directas.

## 10. Cancelación lógica e idempotente

**Decisión.** Cambiar el estado a `CANCELLED` y devolver 204 al repetir la cancelación de una reserva existente.

**Justificación.** Conservar la reserva permite consultar qué intervalo fue registrado y evita perder el recurso al cancelar. La idempotencia permite repetir una petición cuando el cliente no sabe si recibió la respuesta de la primera ejecución. Solo las reservas `ACTIVE` participan en el cálculo de ocupación.

**Consecuencias.** El horario se libera, pero la fila permanece y sigue apareciendo en las consultas. Cancelar un identificador inexistente devuelve 404. El estado cancelado no constituye un registro de auditoría completo: no se almacenan autor, momento ni secuencia de cambios de la cancelación.

## 11. HTTP Basic sin sesiones para las mutaciones

**Decisión.** Permitir consultas públicas y exigir HTTP Basic en POST y DELETE, con un usuario configurado en memoria, contraseña codificada con BCrypt y sesiones deshabilitadas.

**Justificación.** El alcance necesita proteger escrituras sin implementar emisión, renovación o revocación de tokens. HTTP Basic permite autenticar cada petición con los mecanismos de Spring Security. Se deniegan las rutas y métodos no declarados para mantener explícita la superficie disponible.

**Consecuencias.** El cliente envía credenciales en cada petición y necesita TLS fuera de localhost. No hay usuarios persistentes, permisos por propietario ni gestión de identidades. CSRF está deshabilitado para este uso como API; una interfaz web con credenciales gestionadas automáticamente por el navegador requiere reevaluar esa decisión. JWT no resolvería por sí mismo la gestión de identidad: un alcance multiusuario justificaría evaluar OIDC y autorización por recurso.

## 12. Validación en la frontera REST y en los casos de uso

**Decisión.** Validar estructura y campos en los DTO REST, y validar las entradas de los casos de uso antes de acceder a persistencia.

**Justificación.** La validación REST devuelve errores asociados a los campos recibidos. La validación de aplicación evita depender exclusivamente de HTTP: una llamada interna también debe rechazar identificadores, longitudes, capacidades o intervalos inválidos sin provocar errores técnicos accidentales.

**Consecuencias.** Algunas reglas aparecen en ambas fronteras y deben mantenerse alineadas. El dominio conserva sus invariantes básicas sin anotaciones de frameworks. Las restricciones de base de datos siguen siendo necesarias para integridad y concurrencia; la validación previa no las sustituye.

## 13. Problem Details y paginación estable

**Decisión.** Usar Problem Details para errores de aplicación 400/404/409 y ordenar las consultas de reservas por `startAt ASC, id ASC`, con páginas de hasta 100 registros.

**Justificación.** Un formato común de error permite a los clientes distinguir estado, detalle y errores de campos. El segundo criterio de orden desambigua reservas con el mismo inicio; limitar el tamaño reduce el volumen de cada respuesta.

**Consecuencias.** Los errores 401/403 pertenecen a Spring Security y no se prometen en ese formato. El orden es determinista para un conjunto de datos dado, pero la paginación por número no proporciona una instantánea consistente entre peticiones si se insertan datos concurrentemente. Un caso que necesitase esa propiedad requeriría otra estrategia de consulta.

## 14. Pruebas de comportamiento y control de cobertura en CI

**Decisión.** Combinar pruebas unitarias, integración con MockMvc/H2, pruebas de migración y peticiones concurrentes en transacciones independientes. Exigir al menos 80% de instrucciones y ramas mediante JaCoCo en `check`.

**Justificación.** Las pruebas unitarias verifican validaciones y orden de interacción; las de integración verifican seguridad, contratos, transacciones y persistencia. Las migraciones necesitan pruebas sobre datos existentes, y los conflictos concurrentes requieren ejecuciones simultáneas: las pruebas secuenciales no demuestran esa propiedad. 

**Consecuencias.** La cobertura mide ejecución de código, no ausencia de defectos. 
