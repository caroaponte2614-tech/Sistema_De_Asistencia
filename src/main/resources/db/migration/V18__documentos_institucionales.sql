-- =============================================================================
-- V18 — Documentos institucionales: carrusel del portal + lectura chuleada + reporte
--
-- Modelo nuevo y separado de `documento`/`documento_version` (el portafolio personal
-- de cada quien). Acá cada fila es del mismo documento para TODA la empresa —no hay
-- usuario_id dueño—, así que forzarlo dentro del modelo personal habría sido mezclar
-- dos cosas distintas. Tampoco se reutiliza `documento_acceso` (V5, nunca usada): su FK
-- apunta al `documento` personal, y esto necesita su propia tabla de lecturas.
-- =============================================================================

CREATE TABLE documento_institucional (
    id                    CHAR(36)     NOT NULL,
    titulo                VARCHAR(200) NOT NULL,
    descripcion           VARCHAR(500) NULL,
    categoria             VARCHAR(100) NULL,
    archivo_clave         VARCHAR(500) NOT NULL,
    archivo_mime          VARCHAR(100) NULL,
    archivo_tamano_bytes  BIGINT       NULL,
    obligatorio           BOOLEAN      NOT NULL DEFAULT FALSE,
    publicado             BOOLEAN      NOT NULL DEFAULT FALSE,
    archivado             BOOLEAN      NOT NULL DEFAULT FALSE,
    orden                 INT          NULL,
    creado_por_id         CHAR(36)     NOT NULL,
    creado_en             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_doc_inst_creado_por FOREIGN KEY (creado_por_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_documento_institucional_estado ON documento_institucional (publicado, archivado);

-- Una fila por (documento, usuario): "verlo" ES "marcarlo leído" — no hay botón aparte,
-- se registra sola la primera vez que alguien abre el archivo. veces_visto y
-- ultima_vez_en quedan por si el consolidado necesita distinguir quién solo espió una
-- vez de quién lo revisó varias.
CREATE TABLE documento_institucional_vista (
    id               CHAR(36)    NOT NULL,
    documento_id     CHAR(36)    NOT NULL,
    usuario_id       CHAR(36)    NOT NULL,
    primera_vez_en   DATETIME(6) NOT NULL,
    ultima_vez_en    DATETIME(6) NOT NULL,
    veces_visto      INT         NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uq_doc_inst_vista UNIQUE (documento_id, usuario_id),
    CONSTRAINT fk_doc_inst_vista_documento FOREIGN KEY (documento_id) REFERENCES documento_institucional (id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_inst_vista_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_doc_inst_vista_usuario ON documento_institucional_vista (usuario_id);

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('DOCUMENTO_INSTITUCIONAL_VER',          'Ver y marcar como leídos los documentos institucionales', 'DOCUMENTO_INSTITUCIONAL'),
    ('DOCUMENTO_INSTITUCIONAL_ADMINISTRAR',  'Publicar y administrar documentos institucionales',       'DOCUMENTO_INSTITUCIONAL');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('USUARIO',     'DOCUMENTO_INSTITUCIONAL_VER',         'PROPIO', TRUE, FALSE),
    ('COORDINADOR', 'DOCUMENTO_INSTITUCIONAL_VER',         'PROPIO', TRUE, FALSE),
    ('SUPERVISOR',  'DOCUMENTO_INSTITUCIONAL_VER',         'TODOS',  TRUE, FALSE),
    ('SUPERVISOR',  'DOCUMENTO_INSTITUCIONAL_ADMINISTRAR', 'TODOS',  TRUE, FALSE),
    ('ADMIN',       'DOCUMENTO_INSTITUCIONAL_VER',         'TODOS',  TRUE, FALSE),
    ('ADMIN',       'DOCUMENTO_INSTITUCIONAL_ADMINISTRAR', 'TODOS',  TRUE, FALSE);
