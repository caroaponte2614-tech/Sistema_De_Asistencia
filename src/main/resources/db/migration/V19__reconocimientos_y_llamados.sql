-- =============================================================================
-- V19 — Reconocimientos y llamados de atención
--
-- Módulo nuevo y formal, distinto del tag "Reconocimiento" del muro (ese sigue siendo
-- solo una etiqueta de post social — ver V-de-portal, sin registro estructurado). Acá
-- coordinador o Talento Humano registran directamente sobre un colaborador; no hay paso
-- de aprobación, así que no lleva `estado` ni máquina de decisión (más parecido a
-- documento_institucional que a horas_extra_registro).
-- =============================================================================

CREATE TABLE reconocimiento_registro (
    id                     CHAR(36)      NOT NULL,
    colaborador_id         CHAR(36)      NOT NULL,
    tipo                   VARCHAR(20)   NOT NULL,
    categoria              VARCHAR(100)  NULL,
    titulo                 VARCHAR(200)  NOT NULL,
    descripcion            VARCHAR(2000) NOT NULL,
    fecha                  DATE          NOT NULL,
    archivo_soporte_clave  VARCHAR(500)  NULL,
    registrado_por_id      CHAR(36)      NOT NULL,
    creado_en              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_reconocimiento_colaborador    FOREIGN KEY (colaborador_id)    REFERENCES usuario (id),
    CONSTRAINT fk_reconocimiento_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_reconocimiento_tipo CHECK (tipo IN ('RECONOCIMIENTO', 'LLAMADO_ATENCION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_reconocimiento_colaborador ON reconocimiento_registro (colaborador_id, tipo);

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('RECONOCIMIENTO_CREAR', 'Registrar reconocimientos y llamados de atención',              'RECONOCIMIENTO'),
    ('RECONOCIMIENTO_VER',   'Ver los reconocimientos y llamados de atención propios o del equipo', 'RECONOCIMIENTO');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('COORDINADOR', 'RECONOCIMIENTO_CREAR', 'AREA',   TRUE, FALSE),
    ('COORDINADOR', 'RECONOCIMIENTO_VER',   'AREA',   TRUE, FALSE),
    ('SUPERVISOR',  'RECONOCIMIENTO_CREAR', 'TODOS',  TRUE, FALSE),
    ('SUPERVISOR',  'RECONOCIMIENTO_VER',   'TODOS',  TRUE, FALSE),
    ('USUARIO',     'RECONOCIMIENTO_VER',   'PROPIO', TRUE, FALSE),
    ('ADMIN',       'RECONOCIMIENTO_CREAR', 'TODOS',  TRUE, FALSE),
    ('ADMIN',       'RECONOCIMIENTO_VER',   'TODOS',  TRUE, FALSE);
