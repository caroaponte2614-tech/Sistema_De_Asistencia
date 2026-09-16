-- =============================================================================
-- V22 — Miniaturas para las tarjetas del carrusel del portal.
--
-- curso ya tiene miniatura_clave desde V17 (sin usar hasta ahora). documento_institucional
-- no la tenía: se agrega acá para que ambos tipos de tarjeta (cursos y documentos, p.ej.
-- políticas de bienestar/burnout, reglamento de trabajo) puedan mostrar una imagen en el
-- carrusel unificado del portal en vez de solo texto.
-- =============================================================================

ALTER TABLE documento_institucional
    ADD COLUMN miniatura_clave VARCHAR(500) NULL AFTER archivo_tamano_bytes;
