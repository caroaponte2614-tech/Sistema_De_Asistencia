-- =============================================================================
-- V13 — Módulo de usuarios del sistema (ADMIN/SUPERVISOR) y vista de jerarquía
-- (ADMIN/SUPERVISOR/COORDINADOR)
--
-- 1. Capacidad nueva ORGANIGRAMA_VER: una vista de solo lectura de "quién es jefe de
--    quién" para los tres roles que aprueban algo en la cadena (ADMIN, SUPERVISOR,
--    COORDINADOR) — distinta de ORGANIGRAMA_ADMINISTRAR (V11), que sigue siendo solo
--    de ADMIN/SUPERVISOR porque esa sí edita.
--
-- 2. Dos ambitos de V1 que se quedaron cortos frente a lo que ya dice el código y D-021,
--    encontrados al construir la pantalla de "ver los documentos del usuario":
--    - SUPERVISOR tenía DOCUMENTO_RESTRINGIDO_SUBIR en TODOS (V11) pero
--      DOCUMENTO_RESTRINGIDO_VER se había quedado en PROPIO: RH podía subirle un
--      documento restringido a cualquiera pero no podía verlo después. D-021 punto 3 es
--      explícito en que RH administra el expediente restringido de cualquier
--      colaborador, no solo el propio.
--    - DOCUMENTO_PUBLICO_SUBIR estaba en PROPIO para ADMIN y SUPERVISOR, pero
--      DocumentoService.exigirPuedeAdministrar ya está escrito esperando que alguien con
--      alcance TODOS pueda corregir el portafolio público de otro colaborador
--      ("por si RH necesita corregir algo a nombre del colaborador") — el código ya
--      contaba con esto, la matriz nunca se lo dio.
-- =============================================================================

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('ORGANIGRAMA_VER', 'Ver quién es jefe de quién (solo lectura)', 'ADMINISTRACION');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('ADMIN',       'ORGANIGRAMA_VER', 'TODOS', TRUE, FALSE),
    ('SUPERVISOR',  'ORGANIGRAMA_VER', 'TODOS', TRUE, FALSE),
    ('COORDINADOR', 'ORGANIGRAMA_VER', 'AREA',  TRUE, FALSE);

UPDATE rol_capacidad SET ambito = 'TODOS'
 WHERE rol_codigo = 'SUPERVISOR' AND capacidad_codigo = 'DOCUMENTO_RESTRINGIDO_VER';

UPDATE rol_capacidad SET ambito = 'TODOS'
 WHERE rol_codigo IN ('ADMIN', 'SUPERVISOR') AND capacidad_codigo = 'DOCUMENTO_PUBLICO_SUBIR';
