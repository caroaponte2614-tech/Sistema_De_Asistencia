-- =============================================================================
-- V5 — Gestión documental (§4.2)
--
-- Dos categorías con reglas distintas, y VERSIONADO: al resubir no se sobrescribe,
-- se crea la versión n+1 conservando el histórico. Por eso son dos tablas —
-- `documento` es la identidad estable (el contrato de fulano) y `documento_version`
-- son los archivos—. Con una sola tabla, "no sobrescribir" sería una promesa del
-- código; así lo sostiene el esquema.
--
-- El archivo NO vive en la base de datos: la tabla guarda la
-- clave relativa del archivo dentro de STORAGE_PATH.
-- =============================================================================

CREATE TABLE documento_categoria (
    codigo      VARCHAR(40)  NOT NULL,
    nombre      VARCHAR(120) NOT NULL,
    -- RESTRINGIDO = de expediente, solo ADMIN sube, el dueño lo lee.
    -- PUBLICO     = del colaborador, lo sube él mismo.
    clase       VARCHAR(20)  NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (codigo),
    CONSTRAINT ck_doc_categoria_clase CHECK (clase IN ('RESTRINGIDO', 'PUBLICO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Los ejemplos que enumera la tabla del §4.2, ni más ni menos.
INSERT INTO documento_categoria (codigo, nombre, clase) VALUES
    ('CONTRATO',     'Contrato',                  'RESTRINGIDO'),
    ('AFILIACION',   'Afiliaciones',              'RESTRINGIDO'),
    ('EXAMEN',       'Exámenes médicos',          'RESTRINGIDO'),
    ('ACTA',         'Actas',                     'RESTRINGIDO'),
    ('HOJA_VIDA',    'Hoja de vida',              'PUBLICO'),
    ('CERTIFICADO',  'Certificados',              'PUBLICO'),
    ('SOPORTE',      'Soportes',                  'PUBLICO');

CREATE TABLE documento (
    id                CHAR(36)     NOT NULL,
    -- De quién es el expediente. No es quien lo sube: un contrato es del colaborador
    -- aunque lo cargue el ADMIN.
    usuario_id        CHAR(36)     NOT NULL,
    categoria_codigo  VARCHAR(40)  NOT NULL,
    titulo            VARCHAR(200) NOT NULL,
    descripcion       VARCHAR(500) NULL,
    -- Solo aplica a los públicos (§4.2: "según visibilidad configurada").
    -- Los restringidos ignoran esta columna: su regla la manda la matriz de permisos.
    visibilidad       VARCHAR(20)  NOT NULL DEFAULT 'PRIVADO',
    -- Número de la última versión. Denormalizado a propósito: el listado de documentos
    -- lo necesita en cada fila y sin él haría un MAX() por documento.
    version_actual    INT          NOT NULL DEFAULT 0,
    archivado         BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_por_id     CHAR(36)     NOT NULL,
    creado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_documento_usuario   FOREIGN KEY (usuario_id)       REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_documento_categoria FOREIGN KEY (categoria_codigo) REFERENCES documento_categoria (codigo),
    CONSTRAINT fk_documento_creador   FOREIGN KEY (creado_por_id)    REFERENCES usuario (id),
    CONSTRAINT ck_documento_visibilidad CHECK (visibilidad IN ('PRIVADO', 'AREA', 'COMPANIA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_documento_usuario ON documento (usuario_id, categoria_codigo);

CREATE TABLE documento_version (
    id                  CHAR(36)     NOT NULL,
    documento_id        CHAR(36)     NOT NULL,
    numero              INT          NOT NULL,
    nombre_archivo      VARCHAR(255) NOT NULL,
    -- MIME REAL, el detectado leyendo los primeros bytes, no el que declara el
    -- navegador ni el que sugiere la extensión (§4.2).
    mime                VARCHAR(120) NOT NULL,
    tamano_bytes        BIGINT       NOT NULL,
    -- Clave relativa dentro de STORAGE_PATH: nunca una URL ni una ruta absoluta.
    almacenamiento_clave VARCHAR(400) NOT NULL,
    -- Detecta corrupción y evita subir dos veces el mismo archivo.
    hash_sha256         CHAR(64)     NULL,
    subido_por_id       CHAR(36)     NOT NULL,
    subido_en           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- La garantía del versionado, en el esquema: no puede haber dos versiones 3.
    CONSTRAINT uq_documento_version UNIQUE (documento_id, numero),
    CONSTRAINT uq_documento_clave   UNIQUE (almacenamiento_clave),
    CONSTRAINT fk_doc_version_documento FOREIGN KEY (documento_id)  REFERENCES documento (id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_version_subido    FOREIGN KEY (subido_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_doc_version_numero CHECK (numero > 0),
    CONSTRAINT ck_doc_version_tamano CHECK (tamano_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_doc_version_documento ON documento_version (documento_id, numero);

-- Quién abrió o descargó qué documento restringido. El §4.2 no lo pide, pero un
-- expediente laboral sin registro de consultas es justo lo que una auditoría reclama.
CREATE TABLE documento_acceso (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    documento_id CHAR(36)    NOT NULL,
    version_id   CHAR(36)    NULL,
    usuario_id   CHAR(36)    NOT NULL,
    accion       VARCHAR(20) NOT NULL,   -- CONSULTA | DESCARGA
    ip           VARCHAR(45) NULL,
    ocurrido_en  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_doc_acceso_documento FOREIGN KEY (documento_id) REFERENCES documento (id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_acceso_version   FOREIGN KEY (version_id)   REFERENCES documento_version (id) ON DELETE SET NULL,
    CONSTRAINT fk_doc_acceso_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario (id),
    CONSTRAINT ck_doc_acceso_accion CHECK (accion IN ('CONSULTA', 'DESCARGA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_doc_acceso_documento ON documento_acceso (documento_id, ocurrido_en);

-- Enlaza un soporte con la solicitud que lo exige (luto, calamidad — ver
-- solicitud_tipo.requiere_soporte en V4).
CREATE TABLE solicitud_soporte (
    solicitud_id CHAR(36)    NOT NULL,
    documento_id CHAR(36)    NOT NULL,
    adjuntado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (solicitud_id, documento_id),
    CONSTRAINT fk_sol_soporte_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud (id) ON DELETE CASCADE,
    CONSTRAINT fk_sol_soporte_documento FOREIGN KEY (documento_id) REFERENCES documento (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
