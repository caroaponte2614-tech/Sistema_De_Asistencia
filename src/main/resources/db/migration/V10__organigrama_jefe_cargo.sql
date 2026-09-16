-- =============================================================================
-- V10 — Organigrama: jefe (persona) → cargos a su mando
--
-- RH define en un solo lugar quién manda sobre qué cargo. La PK es cargo_id (no
-- compuesta con jefe_id) a propósito: un cargo cuelga de UN jefe a la vez, nunca de
-- dos. Reasignar un cargo a otro jefe es un UPDATE de esta fila, no un conflicto de
-- filas duplicadas.
--
-- Esta tabla no decide nada por sí sola: es la fuente que sugiere
-- `usuario.coordinador_id` (V1) al dar de alta o recargar el cargo de un colaborador.
-- El campo autoritativo para aprobaciones y ámbito AREA sigue siendo
-- usuario.coordinador_id — ver CapacidadService y SolicitudWorkflowService.
-- =============================================================================

CREATE TABLE jefe_cargo (
    cargo_id     CHAR(36)    NOT NULL,
    jefe_id      CHAR(36)    NOT NULL,
    asignado_en  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (cargo_id),
    CONSTRAINT fk_jefe_cargo_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id) ON DELETE CASCADE,
    CONSTRAINT fk_jefe_cargo_jefe  FOREIGN KEY (jefe_id)  REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_jefe_cargo_jefe ON jefe_cargo (jefe_id);
