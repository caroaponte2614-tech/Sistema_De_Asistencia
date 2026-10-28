# School_Plus

Plataforma escolar académica: docentes, estudiantes, clases y asistencia con QR. Spring Boot 4 · Java 21 · MySQL/MariaDB · Thymeleaf + Tailwind.

Corre en local con comandos normales: sin Docker y sin servicios externos.

---

## Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 21 | `java -version` debe decir 21 |
| MySQL o MariaDB | MySQL 8 / MariaDB 10.4+ | instalado en la máquina (XAMPP, servicio de Windows, Homebrew, apt…) |
| Git | cualquiera | |
| Node.js | *opcional* | Maven descarga su propio Node para compilar el CSS; solo hace falta para `npm run watch` |

No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`).

## Instalación local paso a paso

### 1. Clonar

```bash
git clone https://github.com/caroaponte2614-tech/Sistema_De_Asistencia.git
cd Sistema_De_Asistencia
```

### 2. Crear la base de datos

```sql
CREATE DATABASE school_plus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Con XAMPP en Windows, desde PowerShell:

```powershell
& "C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE school_plus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Las tablas **no** se crean a mano: las crea Flyway al arrancar.

### 3. Configurar el `.env`

```bash
cp .env.example .env          # Windows: copy .env.example .env
```

Editar `.env` y revisar como mínimo:

- `DB_USERNAME` / `DB_PASSWORD`: las credenciales de tu MySQL local.
- `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`: la primera cuenta de administrador
  (la contraseña necesita al menos 10 caracteres).
- `REMEMBER_ME_KEY`: el valor de ejemplo sirve en local; en cualquier otro entorno genera
  uno propio con `openssl rand -base64 48`.
- Con MySQL 8 (no MariaDB), descomentar `JPA_DIALECT=org.hibernate.dialect.MySQLDialect`.

### 4. Arrancar

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

La primera vez tarda más: descarga las dependencias, descarga Node, compila Tailwind y
aplica las migraciones. Cuando el log diga `Started SchoolPlusApplication`:

| Qué | URL |
|---|---|
| Aplicación | http://localhost:8080 |
| Salud | http://localhost:8080/actuator/health |

### 5. Entrar

Ingresa con el correo y la contraseña de `BOOTSTRAP_ADMIN_*`. Desde **Administración →
Usuarios** se crean las demás cuentas y se asignan sus roles.

Para probar todos los roles de una vez, `BOOTSTRAP_DEMO_USUARIOS=true` crea
`supervisor@` (rectoría), `coordinador@` y `docente@` con el mismo dominio y contraseña
que el administrador (solo en una base sin ADMIN; nunca en producción).

---

## Comandos

```bash
./mvnw spring-boot:run              # arrancar en desarrollo
./mvnw test                         # tests
./mvnw clean package                # JAR en target/ (compila Tailwind y corre los tests)
./mvnw clean package -DskipTests
java -jar target/school-plus-1.0.0.jar   # correr el JAR (lee el .env del directorio actual)
```

Tailwind en modo watch mientras se editan plantillas (requiere Node instalado):

```bash
cd src/main/frontend && npm install && npm run watch
```

`src/main/resources/static/main.css` es **salida compilada**: no se edita a mano ni se
versiona. Se genera desde `src/main/frontend/styles.css`.

## Estructura

```
src/main/java/com/schoolplus/
├── configuration/     seguridad, WebSocket, arranque
├── controller/        controladores MVC (Thymeleaf) y REST (/api/v1/...)
├── dto/               formularios y vistas
├── model/             entidades JPA y enums
├── repository/        Spring Data JPA
├── services/<modulo>/ lógica de negocio, un paquete por módulo
└── util/
src/main/resources/
├── db/migration/      migraciones Flyway V<n>__descripcion.sql
├── templates/         vistas Thymeleaf
└── static/js/         JavaScript sin framework, un archivo por pantalla
src/main/frontend/     styles.css (tokens del tema) y configuración de Tailwind
```

## Base de datos

El esquema lo manda **Flyway** (`spring.jpa.hibernate.ddl-auto=validate`).

- Migraciones en `src/main/resources/db/migration`, nombradas `V<n>__<descripcion>.sql`.
- Una migración aplicada **no se modifica**: se corrige con una nueva.
- Las claves primarias son UUID v7 en `CHAR(36)`.

## Asistencia con QR

El módulo nuevo de School_Plus. Una **clase** es una asignatura con su profesor, su horario
y sus estudiantes matriculados (`/rectoria/clases`).

### Cómo se usa

1. El profesor entra a **Asistencia de mis clases** (`/docencia/asistencia`), elige la franja
   del horario y la tolerancia, y abre la sesión.
2. La pantalla de proyección muestra un **QR grande**, un **código de 6 dígitos** y el
   contador de presentes en vivo. Hay botón de pantalla completa para el proyector.
3. Cada estudiante escanea con su celular: desde el lector de la app
   (`/estudiante/asistencia`) o con la cámara nativa, porque el QR es un enlace. Si no tiene
   sesión iniciada, entra y el registro se completa solo.
4. Quien no pueda usar la cámara escribe el código de 6 dígitos.
5. Al **cerrar la sesión**, los matriculados que no se registraron quedan **ausentes**. El
   profesor puede corregir: justificar (con motivo obligatorio) o marcar a mano a quien no
   tiene celular.

### Antifraude

| Medida | Cómo funciona |
|---|---|
| QR dinámico | El QR lleva un token firmado con HMAC-SHA256 (sesión + ventana de tiempo). Rota cada 15 s y se acepta una ventana de gracia: vale entre 15 y 30 segundos |
| Código rotativo | Los 6 dígitos se derivan del mismo HMAC y cambian al mismo ritmo |
| Solo con la sesión abierta | Un token de una sesión cerrada no registra nada |
| Celular vinculado | El primer registro vincula el teléfono a la cuenta. Desde otro, el registro se rechaza y queda una solicitud que un profesor de ese estudiante (o un admin) debe autorizar. A sus profesores les llega el aviso en la campanita, y al estudiante cuando se decide |
| Un celular, una persona | Restricciones únicas en la base: un celular activo por estudiante, y un mismo celular no registra a dos personas en la misma sesión |
| Un registro por sesión | Restricción única `(sesion_id, estudiante_id)` |
| Auditoría | Cada registro guarda método, celular, IP, user agent y, si se activa, coordenadas |
| Ubicación (opcional) | `ASISTENCIA_GEO_HABILITADA=true` con las coordenadas del colegio y un radio en metros |

El identificador del celular es una cookie aleatoria que pone el servidor (`HttpOnly`, así
que el JavaScript de la página no puede leerla ni copiarla); en la base solo se guarda su
SHA-256. Borrar los datos del sitio o cambiar de navegador equivale a cambiar de celular.

### Reportes

`/docencia/asistencia/reportes` filtra por clase, estudiante y rango de fechas, y exporta a
**Excel** y **CSV**. Cada estudiante ve su propio historial en `/estudiante/asistencia/historial`.

El porcentaje es `(presentes + tarde) / (sesiones contadas)`; una falta justificada sale del
denominador. Solo entran sesiones **cerradas**.

### Probar desde celulares reales (HTTPS)

La cámara del navegador solo funciona en **contextos seguros**: `https://` o `localhost`. Para
probar con teléfonos de verdad contra el equipo de desarrollo hace falta un túnel:

```bash
# Opción 1: cloudflared (no requiere cuenta)
cloudflared tunnel --url http://localhost:8080

# Opción 2: ngrok
ngrok http 8080
```

El túnel da una URL `https://…`. Hay que ponerla en el `.env` y reiniciar:

```bash
APP_BASE_URL=https://mi-tunel.trycloudflare.com   # el QR apunta aquí y el WebSocket la exige como origen
FORWARD_HEADERS_STRATEGY=framework                # para que la app vea el esquema y la IP reales
COOKIE_SECURE=true
```

Alternativa sin túnel: un certificado local (`mkcert`) y servir la aplicación por HTTPS en la
red local.

El lector funciona en Chrome (Android) con el `BarcodeDetector` nativo y en Safari (iOS) con
un respaldo en WebAssembly que se descarga solo cuando hace falta. Ambos archivos se sirven
desde el propio dominio (`/vendor/...`), porque la política de contenido no admite CDN.

## Matriz de permisos

Los permisos son **datos, no `if`s**: viven en `rol_capacidad` (rol × capacidad × ámbito)
y los aplica `services/permisos/CapacidadService`, expuesto como bean `capacidades`:

```java
@PreAuthorize("@capacidades.puede('USUARIO_ADMINISTRAR')")
```

Apagar una fila en la base cierra la ruta sin recompilar. El ámbito (`PROPIO` / `TODOS`) se comprueba con `capacidades.puedeSobre(codigo, usuarioId)`. El menú lateral se
pinta con el mismo bean, así que nunca muestra un enlace que daría 403.

| Rol | Uso en School_Plus |
|---|---|
| `ADMIN` | Administrador del sistema |
| `SUPERVISOR` | Rectoría: administra clases y usuarios, ve los reportes de toda la institución |
| `COORDINADOR` | Coordinador académico: toma asistencia y ve los reportes de sus clases |
| `USUARIO` | Docente: toma asistencia y ve los reportes de sus clases |
| `ESTUDIANTE` | Estudiante matriculado: solo registra su asistencia y ve su historial |

## Tema visual

Toda la paleta vive en el bloque `:root` de `src/main/frontend/styles.css`, y
`tailwind.config.js` apunta a esas variables. Cambiar un color es cambiar una variable.

| Token | Valor |
|---|---|
| `--color-accent` (primario) | `#2563EB` |
| `--color-accent-600` (hover) | `#1D4ED8` |
| `--color-accent-800` (oscuro) | `#1E3A8A` |
| `--color-accent-100` (claro) | `#DBEAFE` |
| `--color-bg` (fondo suave) | `#EFF6FF` |
| `--color-accent-2` (acento) | `#0EA5E9` |
| `--color-success` / `--color-danger` / `--color-warning` | `#15803D` / `#B91C1C` / `#B45309` |

Los colores semánticos son tokens propios (`.aviso`, `.aviso-ok`, `.tag-danger`,
`.tag-success`, `.tag-warning`): cambiar la marca no cambia el color de un error.

## Correo

Con `MAIL_ENABLED=false` (valor por defecto) el registro y la recuperación de contraseña
**muestran el enlace en pantalla**, para recorrer el flujo completo sin SMTP. Para enviar
correos reales, configurar `MAIL_*` con una cuenta propia.

## Documentación

Las decisiones técnicas y su porqué están en [`docs/DECISIONES.md`](docs/DECISIONES.md).
