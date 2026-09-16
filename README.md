# School_Plus

Plataforma escolar: docentes, estudiantes, clases, capacitaciones, documentos, solicitudes
y portal institucional. Spring Boot 4 · Java 21 · MySQL/MariaDB · Thymeleaf + Tailwind.

Corre en local con comandos normales: sin Docker y sin servicios externos. Los archivos
subidos se guardan en una carpeta del disco.

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
git clone https://github.com/Guio3141592653589793238462643383/School_Plus.git
cd School_Plus
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
`supervisor@`, `coordinador@` y `colaborador@` con el mismo dominio y contraseña que el
administrador (solo en una base sin ADMIN; nunca en producción).

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

## Archivos subidos

Se guardan en `STORAGE_PATH` (por defecto `./uploads`, ignorada por git), en subcarpetas por
tipo: `documentos/`, `soportes/`, `institucionales/`, `videos/`, `imagenes/`,
`certificados/`.

- Cada archivo recibe un nombre UUID; el nombre original solo se guarda para mostrarlo.
- Por tipo se validan extensión, tamaño máximo y firma binaria del contenido
  (`services/documentos/TipoArchivo` y `FormatoArchivo`).
- La base guarda rutas **relativas** (`videos/<uuid>.mp4`), nunca URLs.
- Nada de esa carpeta se sirve como estático: todo sale por un controlador que verifica
  permisos antes de entregar los bytes.

Para respaldar los archivos basta con copiar la carpeta junto con un volcado de la base.

## Estructura

```
src/main/java/com/schoolplus/
├── configuration/     seguridad, WebSocket, almacenamiento, arranque
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

## Matriz de permisos

Los permisos son **datos, no `if`s**: viven en `rol_capacidad` (rol × capacidad × ámbito)
y los aplica `services/permisos/CapacidadService`, expuesto como bean `capacidades`:

```java
@PreAuthorize("@capacidades.puede('USUARIO_ADMINISTRAR')")
```

Apagar una fila en la base cierra la ruta sin recompilar. El ámbito (`PROPIO` / `AREA` /
`TODOS`) se comprueba con `capacidades.puedeSobre(codigo, usuarioId)`. El menú lateral se
pinta con el mismo bean, así que nunca muestra un enlace que daría 403.

| Rol | Uso en School_Plus |
|---|---|
| `ADMIN` | Administrador del sistema |
| `SUPERVISOR` | Rectoría / talento humano: última instancia de aprobación |
| `COORDINADOR` | Coordinador académico o de área: primera instancia de aprobación |
| `USUARIO` | Docente o personal de la institución |

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
