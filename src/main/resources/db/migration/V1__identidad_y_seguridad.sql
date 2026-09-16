-- =============================================================================
-- V1 — Identidad, organigrama y matriz de permisos
--
-- Notas de diseño (ver docs/DECISIONES.md):
--   · PK en CHAR(36) UUID y no INT AUTO_INCREMENT: los ids de los tres sistemas de
--     origen colisionan entre sí (todos arrancan en 1). D-006.
--   · La deduplicación en la migración de usuarios es por CORREO. D-002.
--   · La columna se llama password_hash, no "contraseña" (los tres repos de origen
--     usan una eñe en el nombre de columna). §2.2.7 #8.
--   · La matriz de permisos vive en datos (rol_capacidad), no en ifs. §3.1.
-- =============================================================================

-- ── Catálogos ────────────────────────────────────────────────────────────────

CREATE TABLE sede (
    id          CHAR(36)     NOT NULL,
    nombre      VARCHAR(120) NOT NULL,
    ciudad      VARCHAR(120) NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_sede_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cargo (
    id          CHAR(36)     NOT NULL,
    nombre      VARCHAR(150) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_cargo_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- El área es la unidad del organigrama. Su coordinador se enlaza más abajo, cuando
-- ya existe la tabla usuario (dependencia circular area <-> usuario).
CREATE TABLE area (
    id              CHAR(36)     NOT NULL,
    nombre          VARCHAR(120) NOT NULL,
    coordinador_id  CHAR(36)     NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_area_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Usuario ──────────────────────────────────────────────────────────────────

CREATE TABLE usuario (
    id              CHAR(36)     NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    email           VARCHAR(180) NOT NULL,
    password_hash   VARCHAR(100) NULL,          -- NULL = migrado sin clave utilizable
    cedula          VARCHAR(20)  NULL,
    telefono        VARCHAR(20)  NULL,
    cargo_id        CHAR(36)     NULL,
    sede_id         CHAR(36)     NULL,
    area_id         CHAR(36)     NULL,
    -- Jefe directo. Es la relación que decide quién aprueba en primera instancia:
    -- el coordinador del solicitante, no cualquier coordinador (§4.4).
    coordinador_id  CHAR(36)     NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',   -- ACTIVO | INACTIVO
    fecha_ingreso   DATE         NULL,
    creado_en       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT       NOT NULL DEFAULT 0,          -- @Version (§5.2)
    PRIMARY KEY (id),
    CONSTRAINT uq_usuario_email  UNIQUE (email),
    CONSTRAINT uq_usuario_cedula UNIQUE (cedula),
    CONSTRAINT fk_usuario_cargo       FOREIGN KEY (cargo_id)       REFERENCES cargo (id),
    CONSTRAINT fk_usuario_sede        FOREIGN KEY (sede_id)        REFERENCES sede (id),
    CONSTRAINT fk_usuario_area        FOREIGN KEY (area_id)        REFERENCES area (id),
    CONSTRAINT fk_usuario_coordinador FOREIGN KEY (coordinador_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_usuario_estado      ON usuario (estado);
CREATE INDEX ix_usuario_coordinador ON usuario (coordinador_id);

ALTER TABLE area
    ADD CONSTRAINT fk_area_coordinador FOREIGN KEY (coordinador_id) REFERENCES usuario (id);

-- ── Roles y matriz de permisos ───────────────────────────────────────────────

CREATE TABLE rol (
    codigo      VARCHAR(20)  NOT NULL,
    nombre      VARCHAR(80)  NOT NULL,
    descripcion VARCHAR(255) NULL,
    PRIMARY KEY (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usuario_rol (
    usuario_id  CHAR(36)    NOT NULL,
    rol_codigo  VARCHAR(20) NOT NULL,
    asignado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id, rol_codigo),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_rol_rol     FOREIGN KEY (rol_codigo) REFERENCES rol (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE capacidad (
    codigo      VARCHAR(60)  NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    modulo      VARCHAR(40)  NOT NULL,
    PRIMARY KEY (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Capacidad + ÁMBITO. La matriz del §3.1 mezcla las dos cosas ("ver todos los
-- tableros", "reportes solo de su área"): sin el ámbito, la mitad de las reglas
-- volvería a caer en ifs dentro del código.
CREATE TABLE rol_capacidad (
    rol_codigo             VARCHAR(20) NOT NULL,
    capacidad_codigo       VARCHAR(60) NOT NULL,
    ambito                 VARCHAR(10) NOT NULL,             -- PROPIO | AREA | TODOS
    habilitado             BOOLEAN     NOT NULL DEFAULT TRUE,
    -- TRUE = punto marcado con ❔ en el prompt: queda como flag apagado hasta que
    -- Talento Humano lo confirme. No se resuelve por cuenta propia.
    pendiente_confirmacion BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (rol_codigo, capacidad_codigo),
    CONSTRAINT fk_rol_capacidad_rol       FOREIGN KEY (rol_codigo)       REFERENCES rol (codigo) ON DELETE CASCADE,
    CONSTRAINT fk_rol_capacidad_capacidad FOREIGN KEY (capacidad_codigo) REFERENCES capacidad (codigo) ON DELETE CASCADE,
    CONSTRAINT ck_rol_capacidad_ambito CHECK (ambito IN ('PROPIO', 'AREA', 'TODOS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Trazabilidad con los sistemas de origen (§6.2) ───────────────────────────

CREATE TABLE usuario_origen_externo (
    id              CHAR(36)    NOT NULL,
    usuario_id      CHAR(36)    NOT NULL,
    sistema_origen  VARCHAR(40) NOT NULL,   -- código del sistema externo
    id_en_origen    VARCHAR(60) NOT NULL,
    rol_en_origen   VARCHAR(40) NULL,
    sincronizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_origen_sistema_id UNIQUE (sistema_origen, id_en_origen),
    CONSTRAINT fk_origen_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Alta de cuenta y recuperación de contraseña ──────────────────────────────
-- Origen: un proyecto de referencia (model/UsuarioPendiente.java) (alta con verificación por
-- correo, token de 24 h) y un proyecto de referencia (model/PasswordResetToken.java) (token de
-- 30 min, uno vivo por usuario).

CREATE TABLE usuario_pendiente (
    id            CHAR(36)     NOT NULL,
    nombre        VARCHAR(150) NOT NULL,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    cedula        VARCHAR(20)  NULL,
    telefono      VARCHAR(20)  NULL,
    cargo_id      CHAR(36)     NULL,
    sede_id       CHAR(36)     NULL,
    token         VARCHAR(100) NOT NULL,
    expira_en     DATETIME(6)  NOT NULL,
    creado_en     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_usuario_pendiente_email UNIQUE (email),
    CONSTRAINT uq_usuario_pendiente_token UNIQUE (token),
    CONSTRAINT fk_pendiente_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id),
    CONSTRAINT fk_pendiente_sede  FOREIGN KEY (sede_id)  REFERENCES sede (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset_token (
    id         CHAR(36)     NOT NULL,
    usuario_id CHAR(36)     NOT NULL,
    token      VARCHAR(100) NOT NULL,
    expira_en  DATETIME(6)  NOT NULL,
    usado_en   DATETIME(6)  NULL,
    creado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token UNIQUE (token),
    CONSTRAINT fk_password_reset_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_password_reset_usuario ON password_reset_token (usuario_id);

-- ── Bitácora de accesos (§4.1) ───────────────────────────────────────────────
-- Ninguno de los tres repos de referencia la tenía.

CREATE TABLE auditoria_acceso (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id      CHAR(36)     NULL,          -- NULL si el correo no existe
    email_intentado VARCHAR(180) NULL,
    evento          VARCHAR(30)  NOT NULL,      -- LOGIN_OK | LOGIN_FALLIDO | LOGOUT |
                                                -- RESET_SOLICITADO | RESET_APLICADO |
                                                -- REGISTRO_SOLICITADO | CUENTA_VERIFICADA |
                                                -- BLOQUEO_RATE_LIMIT
    ip              VARCHAR(45)  NULL,
    user_agent      VARCHAR(255) NULL,
    correlation_id  VARCHAR(36)  NULL,
    detalle         VARCHAR(255) NULL,
    ocurrido_en     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_auditoria_ocurrido ON auditoria_acceso (ocurrido_en);
CREATE INDEX ix_auditoria_email    ON auditoria_acceso (email_intentado, ocurrido_en);

-- =============================================================================
-- DATOS INICIALES
-- =============================================================================

INSERT INTO rol (codigo, nombre, descripcion) VALUES
    ('ADMIN',       'Administrador', 'Administrador del sistema.'),
    ('SUPERVISOR',  'Rectoría',      'Rectoría y talento humano. Última instancia de aprobación.'),
    ('COORDINADOR', 'Coordinador',   'Coordinador académico o de área. Primera instancia de aprobación.'),
    ('USUARIO',     'Docente',       'Docente o personal de la institución.');

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('DOCUMENTO_RESTRINGIDO_SUBIR', 'Subir documentos restringidos de expediente',       'DOCUMENTOS'),
    ('DOCUMENTO_RESTRINGIDO_VER',   'Ver documentos restringidos',                       'DOCUMENTOS'),
    ('DOCUMENTO_PUBLICO_SUBIR',     'Subir documentos públicos del colaborador',         'DOCUMENTOS'),
    ('TABLERO_USAR',                'Usar el tablero de tareas',                         'TABLERO'),
    ('TABLERO_VER',                 'Ver tableros de otras personas',                    'TABLERO'),
    ('SOLICITUD_CREAR',             'Solicitar permisos y vacaciones',                   'SOLICITUDES'),
    ('SOLICITUD_APROBAR_PRIMERA',   'Aprobar en primera instancia (coordinación)',       'SOLICITUDES'),
    ('SOLICITUD_APROBAR_SEGUNDA',   'Aprobar en segunda instancia (Talento Humano)',     'SOLICITUDES'),
    ('HORAS_EXTRA_REGISTRAR',       'Registrar horas extras del equipo a cargo',         'HORAS_EXTRA'),
    ('PORTAL_PUBLICAR',             'Publicar posts en el muro',                         'PORTAL'),
    ('PORTAL_ENCUESTA_CREAR',       'Crear encuestas en el muro',                        'PORTAL'),
    ('PORTAL_COMENTAR',             'Comentar y reaccionar en el muro',                  'PORTAL'),
    ('PORTAL_MODERAR',              'Ocultar o eliminar comentarios',                    'PORTAL'),
    ('REPORTE_GENERAR',             'Generar reportes en Excel',                         'REPORTES'),
    ('USUARIO_ADMINISTRAR',         'Administrar usuarios, roles y organigrama',         'ADMINISTRACION');

-- Matriz del §3.1. Las filas con pendiente_confirmacion = TRUE están APAGADAS a la
-- espera de confirmación de Talento Humano (los ❔ del prompt).
INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    -- ADMIN
    ('ADMIN', 'DOCUMENTO_RESTRINGIDO_SUBIR', 'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'DOCUMENTO_RESTRINGIDO_VER',   'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'DOCUMENTO_PUBLICO_SUBIR',     'PROPIO', TRUE,  FALSE),
    ('ADMIN', 'TABLERO_USAR',                'PROPIO', TRUE,  FALSE),
    ('ADMIN', 'TABLERO_VER',                 'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'SOLICITUD_CREAR',             'PROPIO', TRUE,  FALSE),
    ('ADMIN', 'PORTAL_PUBLICAR',             'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'PORTAL_ENCUESTA_CREAR',       'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'PORTAL_COMENTAR',             'PROPIO', TRUE,  FALSE),
    ('ADMIN', 'PORTAL_MODERAR',              'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'REPORTE_GENERAR',             'TODOS',  TRUE,  FALSE),
    ('ADMIN', 'USUARIO_ADMINISTRAR',         'TODOS',  TRUE,  FALSE),

    -- SUPERVISOR (Talento Humano)
    ('SUPERVISOR', 'DOCUMENTO_RESTRINGIDO_SUBIR', 'TODOS',  FALSE, TRUE),   -- ❔
    ('SUPERVISOR', 'DOCUMENTO_RESTRINGIDO_VER',   'PROPIO', TRUE,  FALSE),
    ('SUPERVISOR', 'DOCUMENTO_PUBLICO_SUBIR',     'PROPIO', TRUE,  FALSE),
    ('SUPERVISOR', 'TABLERO_USAR',                'PROPIO', TRUE,  FALSE),
    ('SUPERVISOR', 'SOLICITUD_CREAR',             'PROPIO', TRUE,  FALSE),
    ('SUPERVISOR', 'SOLICITUD_APROBAR_SEGUNDA',   'TODOS',  TRUE,  FALSE),
    ('SUPERVISOR', 'PORTAL_PUBLICAR',             'TODOS',  TRUE,  FALSE),
    ('SUPERVISOR', 'PORTAL_ENCUESTA_CREAR',       'TODOS',  TRUE,  FALSE),
    ('SUPERVISOR', 'PORTAL_COMENTAR',             'PROPIO', TRUE,  FALSE),
    ('SUPERVISOR', 'PORTAL_MODERAR',              'TODOS',  TRUE,  FALSE),
    ('SUPERVISOR', 'REPORTE_GENERAR',             'TODOS',  TRUE,  FALSE),

    -- COORDINADOR (jefe de área)
    ('COORDINADOR', 'DOCUMENTO_RESTRINGIDO_VER', 'PROPIO', TRUE,  FALSE),
    ('COORDINADOR', 'DOCUMENTO_PUBLICO_SUBIR',   'PROPIO', TRUE,  FALSE),
    ('COORDINADOR', 'TABLERO_USAR',              'PROPIO', TRUE,  FALSE),
    ('COORDINADOR', 'TABLERO_VER',               'AREA',   FALSE, TRUE),    -- ❔
    ('COORDINADOR', 'SOLICITUD_CREAR',           'PROPIO', TRUE,  FALSE),
    ('COORDINADOR', 'SOLICITUD_APROBAR_PRIMERA', 'AREA',   TRUE,  FALSE),
    ('COORDINADOR', 'HORAS_EXTRA_REGISTRAR',     'AREA',   TRUE,  FALSE),
    ('COORDINADOR', 'PORTAL_COMENTAR',           'PROPIO', TRUE,  FALSE),
    ('COORDINADOR', 'REPORTE_GENERAR',           'AREA',   FALSE, TRUE),    -- ❔

    -- USUARIO (colaborador)
    ('USUARIO', 'DOCUMENTO_RESTRINGIDO_VER', 'PROPIO', TRUE, FALSE),
    ('USUARIO', 'DOCUMENTO_PUBLICO_SUBIR',   'PROPIO', TRUE, FALSE),
    ('USUARIO', 'TABLERO_USAR',              'PROPIO', TRUE, FALSE),
    ('USUARIO', 'SOLICITUD_CREAR',           'PROPIO', TRUE, FALSE),
    ('USUARIO', 'PORTAL_COMENTAR',           'PROPIO', TRUE, FALSE);

-- Catálogo de sedes de la institución. Agregar una sede es un INSERT, no un cambio de
-- código.
INSERT INTO sede (id, nombre) VALUES
    (UUID(), 'SEDE PRINCIPAL'),
    (UUID(), 'SEDE PRIMARIA'),
    (UUID(), 'SEDE BACHILLERATO');

-- Catálogo de cargos del personal de la institución.
INSERT INTO cargo (id, nombre) VALUES
    (UUID(), 'NO APLICA'),
    (UUID(), 'RECTOR/A'),
    (UUID(), 'COORDINADOR/A ACADEMICO'),
    (UUID(), 'COORDINADOR/A DE CONVIVENCIA'),
    (UUID(), 'DOCENTE'),
    (UUID(), 'DOCENTE DE PREESCOLAR'),
    (UUID(), 'DOCENTE DE PRIMARIA'),
    (UUID(), 'DOCENTE DE BACHILLERATO'),
    (UUID(), 'ORIENTADOR/A ESCOLAR'),
    (UUID(), 'SECRETARIO/A ACADEMICO'),
    (UUID(), 'AUXILIAR ADMINISTRATIVO'),
    (UUID(), 'BIBLIOTECARIO/A'),
    (UUID(), 'SERVICIOS GENERALES'),
    (UUID(), 'ESTUDIANTE');
