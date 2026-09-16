-- =============================================================================
-- V16 — Horas extras: aprobación por registro, sin periodos (§4.6, ajustado 2026-09-08)
--
-- V8 modelaba el corte agrupando en "periodos" que alguien abre y cierra. El
-- requerimiento real es otro: el coordinador registra las horas de su gente y el
-- supervisor de Talento Humano aprueba o rechaza CADA registro — no hay paso de
-- abrir/cerrar un periodo completo. horas_extra_periodo no tiene ningún código Java
-- que la use desde que se creó en V8, así que se elimina en vez de dejarla como deuda
-- muerta sin dueño.
--
-- recargo_tipo y horas_extra_tope no dependían de periodos: quedan tal cual (el
-- catálogo de recargos sigue con factor NULL hasta que Talento Humano confirme las
-- cifras legales, y los topes siguen vacíos a propósito).
-- =============================================================================

ALTER TABLE horas_extra_registro DROP FOREIGN KEY fk_horas_periodo;
ALTER TABLE horas_extra_registro DROP INDEX uq_horas_extra_registro;
ALTER TABLE horas_extra_registro DROP INDEX ix_horas_periodo_colaborador;
ALTER TABLE horas_extra_registro DROP COLUMN periodo_id;

DROP TABLE horas_extra_periodo;

-- Estado de la aprobación (mismo espíritu que EstadoSolicitud, pero de una sola
-- instancia: quien registra —el coordinador— nunca es quien decide, así que no hace
-- falta una máquina de dos pasos).
ALTER TABLE horas_extra_registro
    ADD COLUMN estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE' AFTER horas,
    ADD COLUMN decidido_por_id CHAR(36)     NULL AFTER registrado_por_id,
    ADD COLUMN decidido_en     DATETIME(6)  NULL AFTER decidido_por_id,
    ADD COLUMN motivo_rechazo  VARCHAR(300) NULL AFTER decidido_en;

ALTER TABLE horas_extra_registro
    ADD CONSTRAINT ck_horas_estado    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    ADD CONSTRAINT fk_horas_decidido  FOREIGN KEY (decidido_por_id) REFERENCES usuario (id);

CREATE INDEX ix_horas_estado ON horas_extra_registro (estado);

-- Faltaba la mitad de la matriz: el supervisor no tenía ninguna capacidad de horas
-- extra (solo el coordinador, que registra). Se agrega la de aprobar, y la de que
-- cualquiera vea lo que se registró sobre sí mismo.
INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('HORAS_EXTRA_APROBAR', 'Aprobar o rechazar horas extras registradas', 'HORAS_EXTRA'),
    ('HORAS_EXTRA_VER',     'Ver las horas extras propias',                'HORAS_EXTRA');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('SUPERVISOR',  'HORAS_EXTRA_APROBAR', 'TODOS',  TRUE, FALSE),
    ('COORDINADOR', 'HORAS_EXTRA_VER',     'PROPIO', TRUE, FALSE),
    ('USUARIO',     'HORAS_EXTRA_VER',     'PROPIO', TRUE, FALSE),
    ('ADMIN',       'HORAS_EXTRA_APROBAR', 'TODOS',  TRUE, FALSE),
    ('ADMIN',       'HORAS_EXTRA_VER',     'TODOS',  TRUE, FALSE);
