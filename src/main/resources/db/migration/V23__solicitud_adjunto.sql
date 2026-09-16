-- =============================================================================
-- V23 — Adjunto opcional en una solicitud de permiso/vacaciones.
--
-- Un solo archivo por solicitud (p.ej. un soporte médico o una excusa) — no hace falta
-- una tabla aparte como documento_institucional, que sí necesita versionado y un
-- catálogo propio. Aquí es un dato más de la solicitud, igual que motivo.
-- =============================================================================

ALTER TABLE solicitud
    ADD COLUMN archivo_clave            VARCHAR(500) NULL,
    ADD COLUMN archivo_nombre_original  VARCHAR(255) NULL,
    ADD COLUMN archivo_mime             VARCHAR(100) NULL,
    ADD COLUMN archivo_tamano_bytes     BIGINT       NULL;
