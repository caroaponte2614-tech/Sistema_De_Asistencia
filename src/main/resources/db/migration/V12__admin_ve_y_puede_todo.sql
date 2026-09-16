-- =============================================================================
-- V12 — ADMIN ve y puede todo lo que ven y pueden los otros tres roles
--
-- Decisión del usuario (2026-09-04): "El admin debe ver todo y ya". Hasta ahora la
-- matriz del §3.1 (V1) no le daba a ADMIN ni SOLICITUD_APROBAR_PRIMERA ni
-- SOLICITUD_APROBAR_SEGUNDA — se detectó al probar la pantalla de organigrama con la
-- cuenta admin@schoolplus.example y toparse con un 403 en /talento-humano/organigrama,
-- que además reveló que /talento-humano/** y /coordinacion/** en SecurityConfig
-- exigían el rol exacto (SUPERVISOR / COORDINADOR) sin dejar pasar a ADMIN — eso se
-- corrige aparte, en el código, con hasAnyRole(...).
--
-- HORAS_EXTRA_REGISTRAR entra también aunque esa pantalla todavía no existe (sigue
-- "Próximamente" en el menú): así el día que se construya, ADMIN ya la tiene sin
-- necesitar otra migración para lo mismo.
-- =============================================================================

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('ADMIN', 'SOLICITUD_APROBAR_PRIMERA', 'TODOS', TRUE, FALSE),
    ('ADMIN', 'SOLICITUD_APROBAR_SEGUNDA', 'TODOS', TRUE, FALSE),
    ('ADMIN', 'HORAS_EXTRA_REGISTRAR',     'TODOS', TRUE, FALSE);
