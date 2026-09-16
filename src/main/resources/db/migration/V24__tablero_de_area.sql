-- =============================================================================
-- V24 — Tablero de área
--
-- Hasta ahora un tablero solo podía pertenecer a un usuario (V6: "cada usuario tiene el
-- suyo"). Esta migración le permite pertenecer, en cambio, a un área: uno solo por área,
-- compartido por su equipo y gestionado por su líder (area.coordinador_id, que ya existía
-- desde la V1 pero sin ninguna pantalla que lo usara).
--
-- Se reutilizan las mismas tablas de tablero/columna/tarjeta en vez de duplicarlas: un
-- tablero de área nace con las mismas cuatro columnas y el mismo modelo de tarjetas que uno
-- personal, solo cambia de quién es dueño.
-- =============================================================================

ALTER TABLE tablero
    MODIFY usuario_id CHAR(36) NULL,
    ADD COLUMN area_id CHAR(36) NULL,
    -- Un tablero por área, igual que uno por usuario: la restricción única es lo que hace
    -- cumplir la regla sin depender del código.
    ADD CONSTRAINT uq_tablero_area UNIQUE (area_id),
    ADD CONSTRAINT fk_tablero_area FOREIGN KEY (area_id) REFERENCES area (id) ON DELETE CASCADE,
    -- Dueño exclusivo: o es de un usuario, o es de un área, nunca los dos ni ninguno.
    ADD CONSTRAINT ck_tablero_dueno CHECK (
        (usuario_id IS NOT NULL AND area_id IS NULL) OR
        (usuario_id IS NULL AND area_id IS NOT NULL)
    );
