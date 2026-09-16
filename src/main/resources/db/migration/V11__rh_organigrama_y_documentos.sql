-- =============================================================================
-- V11 — RH pasa a administrar el organigrama, los usuarios y los documentos
-- restringidos
--
-- Tres decisiones tomadas por el usuario el 2026-09-02 (docs/DECISIONES.md D-017):
--
--   1. El organigrama (jefe_cargo de V10, y el override manual de
--      usuario.coordinador_id) lo administra RH (rol SUPERVISOR), no solo ADMIN.
--      Se crea una capacidad propia y más angosta que USUARIO_ADMINISTRAR para no
--      darle a RH permisos de roles/seguridad que no le corresponden.
--
--   2. RH también hace el CRUD de usuarios (alta, edición, baja lógica). Se le
--      concede USUARIO_ADMINISTRAR, que ya existía para ADMIN.
--
--   3. Queda resuelto el ❔ de V1 sobre DOCUMENTO_RESTRINGIDO_SUBIR para SUPERVISOR:
--      RH sube, edita y borra los documentos restringidos de expediente de
--      cualquier colaborador (además de ADMIN, que ya lo tenía). No hay capacidad
--      separada para "editar" o "borrar": igual que el resto de la matriz, quien
--      tiene la capacidad de SUBIR administra el recurso completo.
-- =============================================================================

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('ORGANIGRAMA_ADMINISTRAR', 'Asignar jefes, cargos a su mando y coordinador de cada colaborador', 'ADMINISTRACION');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('ADMIN',      'ORGANIGRAMA_ADMINISTRAR', 'TODOS', TRUE, FALSE),
    ('SUPERVISOR', 'ORGANIGRAMA_ADMINISTRAR', 'TODOS', TRUE, FALSE),
    ('SUPERVISOR', 'USUARIO_ADMINISTRAR',     'TODOS', TRUE, FALSE);

-- Ya no es un ❔: RH sube documentos restringidos de expediente (decisión 3 arriba).
UPDATE rol_capacidad
   SET habilitado = TRUE, pendiente_confirmacion = FALSE
 WHERE rol_codigo = 'SUPERVISOR' AND capacidad_codigo = 'DOCUMENTO_RESTRINGIDO_SUBIR';
