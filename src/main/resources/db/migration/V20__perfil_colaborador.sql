-- =============================================================================
-- V20 — Identificación del colaborador: ficha estructurada que llena el supervisor
--
-- Un registro padre 1:1 (contacto de emergencia, datos que no se repiten) más dos
-- tablas hijas repetibles. perfil_evaluacion es de SOLO AGREGAR: una evaluación pasada
-- es un hecho histórico, no se edita ni se borra (mismo espíritu que documento_version).
-- perfil_hijo describe el estado actual y se reemplaza completo en cada guardado.
-- =============================================================================

CREATE TABLE perfil_colaborador (
    usuario_id                      CHAR(36)     NOT NULL,
    contacto_emergencia_nombre      VARCHAR(150) NULL,
    contacto_emergencia_parentesco  VARCHAR(50)  NULL,
    contacto_emergencia_telefono    VARCHAR(20)  NULL,
    actualizado_por_id              CHAR(36)     NULL,
    actualizado_en                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_perfil_colaborador_usuario FOREIGN KEY (usuario_id)         REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_perfil_actualizado_por     FOREIGN KEY (actualizado_por_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE perfil_hijo (
    id                CHAR(36)     NOT NULL,
    usuario_id        CHAR(36)     NOT NULL,
    nombre            VARCHAR(150) NOT NULL,
    fecha_nacimiento  DATE         NOT NULL,
    orden             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_perfil_hijo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_perfil_hijo_usuario ON perfil_hijo (usuario_id);

CREATE TABLE perfil_evaluacion (
    id               CHAR(36)      NOT NULL,
    usuario_id       CHAR(36)      NOT NULL,
    fecha            DATE          NOT NULL,
    calificacion     VARCHAR(50)   NOT NULL,
    comentario       VARCHAR(2000) NULL,
    evaluado_por_id  CHAR(36)      NOT NULL,
    creado_en        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_perfil_evaluacion_usuario  FOREIGN KEY (usuario_id)      REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_perfil_evaluacion_evaluado FOREIGN KEY (evaluado_por_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_perfil_evaluacion_usuario ON perfil_evaluacion (usuario_id, fecha);

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('PERFIL_COLABORADOR_ADMINISTRAR', 'Editar la identificación estructurada de un colaborador', 'PERFIL_COLABORADOR'),
    ('PERFIL_COLABORADOR_VER',         'Ver la identificación estructurada propia o del equipo',   'PERFIL_COLABORADOR');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('COORDINADOR', 'PERFIL_COLABORADOR_ADMINISTRAR', 'AREA',   TRUE, FALSE),
    ('COORDINADOR', 'PERFIL_COLABORADOR_VER',         'AREA',   TRUE, FALSE),
    ('SUPERVISOR',  'PERFIL_COLABORADOR_ADMINISTRAR', 'TODOS',  TRUE, FALSE),
    ('SUPERVISOR',  'PERFIL_COLABORADOR_VER',         'TODOS',  TRUE, FALSE),
    ('USUARIO',     'PERFIL_COLABORADOR_VER',         'PROPIO', TRUE, FALSE),
    ('ADMIN',       'PERFIL_COLABORADOR_ADMINISTRAR', 'TODOS',  TRUE, FALSE),
    ('ADMIN',       'PERFIL_COLABORADOR_VER',         'TODOS',  TRUE, FALSE);
