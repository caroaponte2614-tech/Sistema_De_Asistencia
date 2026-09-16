# DECISIONES — School_Plus

Registro de decisiones de arquitectura. Formato: qué se decidió, qué alternativa se
descartó y por qué. Una entrada por decisión. Los números se conservan porque el código y
las migraciones los citan (`ver D-0xx`).

School_Plus nace como copia independiente de una plataforma de gestión de personas ya en
funcionamiento: se heredan su arquitectura, sus capas y sus convenciones, y sobre esa base
se agregan el modelo escolar y la asistencia con QR.

---

## D-002 · Los usuarios se identifican por correo
El correo es único (`uq_usuario_email`) y es el nombre de usuario del login. La cédula se
modela aparte, opcional y única cuando está presente.

## D-004 · Spring Boot 4.0.2 / Java 21 / MySQL-MariaDB / Thymeleaf
Monolito con vistas renderizadas en servidor y JavaScript sin framework.
- **Descartado:** SPA (React/Vue) + API REST pura. Obligaría a rehacer todas las vistas.

## D-005 · Flyway desde el día 1, `ddl-auto=validate`
El esquema lo manda Flyway; Hibernate solo valida. Una migración aplicada no se modifica: se
corrige con una nueva.

## D-006 · PK en UUID v7 en vez de INT autoincremental
- **Motivo:** ids que no colisionan al integrar datos de otros sistemas y que no revelan
  cuántos registros hay. La versión 7 es ordenable en el tiempo, lo que evita fragmentar el
  índice agrupado de InnoDB.

## D-007 · Sin Redis
Spring Session JDBC deja el backend sin estado en memoria. Si algún día hay varias
instancias, el WebSocket necesitará un relay (RabbitMQ) y, si hace falta, Redis como caché.

## D-008 · Contraseñas: `DelegatingPasswordEncoder` con bcrypt cost 12
Lee hashes `$2a$10$` heredados y re-cifra a cost 12. Argon2 rompería la compatibilidad.

## D-009 · Un solo sistema de diseño para toda la aplicación
`src/main/frontend/styles.css` es la **única fuente de verdad** de los tokens (color,
tipografía Archivo, radios, sombras) y de los componentes (`.btn`, `.input`, `.card`, `.tag`,
`.aviso`, `.table`, `.dialog`). `tailwind.config.js` no repite valores: apunta a las
variables CSS. Retocar un token cambia a la vez componentes y utilidades.
- **Coste:** los modificadores de opacidad de Tailwind (`bg-accent/50`) no funcionan sobre
  `var()`. Las mezclas se resuelven con `color-mix()`.

## D-010 · El límite de intentos cuenta filas de la bitácora
Login y recuperación se limitan contando filas recientes de `auditoria_acceso` por cuenta y
por IP. Un contador en memoria se perdería en cada reinicio y se duplicaría con dos nodos.

## D-011 · "Mantener sesión activa" con token en base de datos
`PersistentTokenBasedRememberMeServices` sobre `persistent_logins`: permite revocar los
recordatorios al cambiar la contraseña. `REMEMBER_ME_KEY` es obligatoria.

## D-012 · Compatible con MariaDB 10.4 y MySQL 8
Las migraciones evitan sintaxis exclusiva de un motor (nada de índices funcionales ni
parciales). Donde haría falta un índice parcial se usa una columna nullable con `UNIQUE`
que el código llena solo mientras la regla aplica (ver D-035).

## D-013 · Una sola maquinaria de aprobación, parametrizada por tipo
Permisos y vacaciones comparten tabla `solicitud`, estados e historial; lo que los distingue
vive en `solicitud_tipo`.

## D-014 · Lo que no está definido se modela como datos, no como esquema
Periodos de horas extra, tipos de recargo y topes son catálogos: las respuestas pendientes
se cargan con `UPDATE`s, no con migraciones.

## D-015 · Causación de vacaciones: 1,25 días por mes
Parámetro `app.vacaciones.dias-por-mes`, no código.

## D-016 · Un coordinador que pide permiso salta la primera instancia
Detrás de `app.solicitudes.coordinador-salta-primera-instancia` (por defecto `true`).

## D-017 · Dialecto de Hibernate explícito y documentos de expediente
`spring.jpa.database-platform=org.hibernate.dialect.MariaDBDialect` (MariaDB se anuncia como
MySQL 5.5 y la detección automática elige mal). `JPA_DIALECT` lo cambia para MySQL 8.
Los documentos de expediente tienen carpeta pública (la administra el dueño) y restringida
(solo quien tenga `DOCUMENTO_RESTRINGIDO_SUBIR`), con versionado: nunca se sobrescribe.

## D-018 · El menú se pinta por capacidad, no por rol
El sidebar pregunta al mismo bean `capacidades` que los `@PreAuthorize`: nunca aparece un
enlace que al pulsarlo daría 403.

## D-019 · Las barras de las encuestas son 21 clases fijas
`barra-0` … `barra-100` de 5 en 5: la CSP prohíbe `style=""` en línea.

## D-020 · Concurrencia: `@Version` + 409
Las entidades que dos actores pueden tocar a la vez llevan `@Version`. El conflicto se
traduce a **409** con un mensaje entendible, nunca a 500.

## D-021 · Talento humano administra organigrama, usuarios y documentos restringidos
El rol SUPERVISOR (rectoría / talento humano) tiene `ORGANIGRAMA_ADMINISTRAR`,
`USUARIO_ADMINISTRAR` y `DOCUMENTO_RESTRINGIDO_SUBIR`. `usuario.coordinador_id` sigue siendo
la única fuente de "quién es jefe de quién" que leen las aprobaciones y los permisos.

## D-022 · ADMIN entra a todo lo que entran los otros roles
`/talento-humano/**` y `/coordinacion/**` admiten también a ADMIN, y la matriz le concede
las capacidades de aprobación con ámbito TODOS.

## D-023 · Módulo de usuarios y jerarquía de solo lectura
`/talento-humano/usuarios` (alta, edición, roles, foto, jefe, documentos) y
`/mi-espacio/organigrama` (árbol de solo lectura, capacidad `ORGANIGRAMA_VER`).

## D-025 · Modal propio, notificaciones con WebSocket y muro sin recargar
WebSocket crudo (sin STOMP) en `/ws/notificaciones`; la fila en base es la fuente de verdad y
el socket solo avisa. El JSON del socket se arma a mano: Spring Boot 4 autoconfigura Jackson 3
y mezclarlo con el Jackson clásico tumba el arranque.

## D-030 · Tablero de área
Un tablero puede pertenecer a un usuario o a un área (nunca a los dos), reutilizando las
mismas tablas de columnas y tarjetas (V24).

---

## D-031 · Ejecución local, sin Docker
**Estado:** decidido.

La aplicación corre con `./mvnw spring-boot:run` contra un MySQL/MariaDB instalado en la
máquina. Se eliminaron `Dockerfile`, `docker-compose.yml` y el job de imagen del CI.
- **Consecuencia:** `.env` (leído por `DotenvEnvironmentPostProcessor`) es la única
  configuración local; `.env.example` trae valores listos para desarrollo.

## D-032 · Almacenamiento de archivos en disco local
**Estado:** decidido.

`LocalDocumentoAlmacenamiento` implementa la misma interfaz `DocumentoAlmacenamiento` que
antes implementaba el cliente S3, así que ningún servicio cambió de firma.
- Carpeta configurable (`STORAGE_PATH`, por defecto `./uploads`), fuera de `static/`: ningún
  archivo se sirve como estático, todos salen por un controlador que verifica permisos.
- `PoliticaArchivos` centraliza la validación y la clave: `<carpeta del tipo>/<uuid>.<ext>`.
  Por tipo (`TipoArchivo`) se fijan formatos y tamaño máximo; por formato (`FormatoArchivo`)
  el MIME canónico y la firma binaria (PDF, PNG, JPEG, WEBP, Office). El nombre original
  nunca toca el disco.
- Contra path traversal, la clave se normaliza y se exige que la ruta final siga dentro de
  la raíz.
- En la base solo hay claves relativas; nunca hubo URLs guardadas, así que no hizo falta
  migración de datos.

## D-033 · Paleta azul con colores semánticos separados
**Estado:** decidido.

Primario `#2563EB`, hover `#1D4ED8`, oscuro `#1E3A8A`, claro `#DBEAFE`, fondo `#EFF6FF`,
acento `#0EA5E9`, neutros slate. Todo en el bloque `:root` de `styles.css`.
- Antes el acento era rojo y se usaba también como "error". Ahora hay tokens semánticos
  propios (`--color-success`, `--color-danger`, `--color-warning`) y clases `.aviso-ok`,
  `.aviso-info`, `.tag-success`, `.tag-danger`, `.tag-warning`: cambiar la marca ya no
  cambia el color de un error.
- Contraste AA verificado para los pares de texto usados (primario con texto blanco 5,2:1;
  acento-700 y 800 sobre el fondo suave; semánticos 800 sobre su fondo 100).
- Los correos HTML repiten los valores en línea: los clientes de correo no leen variables.
- No existía modo oscuro, así que no hubo que ajustarlo.
