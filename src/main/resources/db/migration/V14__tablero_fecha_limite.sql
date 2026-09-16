-- =============================================================================
-- V14 — Fecha y hora límite en las tarjetas del tablero
--
-- Pedido del usuario (2026-09-04): "en el tablero de tareas añade fecha y hora".
-- `creado_en` ya existía (V6) pero no se pintaba en la tarjeta; se suma `fecha_limite`
-- (opcional, con hora) para que cada tarea pueda llevar un vencimiento.
--
-- `recordatorio_enviado` es el bit que usa el job diario (TableroRecordatorioJob) para
-- no notificar dos veces el mismo vencimiento: se pone en TRUE al avisar, y vuelve a
-- FALSE si la fecha límite cambia (una fecha nueva merece su propio aviso).
-- =============================================================================

ALTER TABLE tablero_tarjeta
    ADD COLUMN fecha_limite DATETIME NULL AFTER prioridad,
    ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE AFTER fecha_limite;

CREATE INDEX ix_tablero_tarjeta_fecha_limite ON tablero_tarjeta (fecha_limite);
