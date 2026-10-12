-- =============================================================================
-- V26 — School_Plus queda solo con lo académico
--
-- Se retiran los módulos laborales (talento humano): solicitudes y vacaciones, horas
-- extra, documentos de expediente, documentos institucionales, capacitaciones,
-- organigrama, tableros, muro, reconocimientos, perfil del colaborador y certificados
-- laborales. Quedan identidad y seguridad, notificaciones, clases y asistencia.
--
-- Las migraciones anteriores no se tocan (una migración aplicada no se modifica): esta
-- deshace sus tablas y columnas.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── Tablas de los módulos laborales ──────────────────────────────────────────
DROP TABLE IF EXISTS
    solicitud_soporte,
    solicitud_historial,
    solicitud,
    solicitud_tipo,
    vacaciones_movimiento,
    vacaciones_saldo,
    horas_extra_registro,
    horas_extra_periodo,
    horas_extra_tope,
    recargo_tipo,
    documento_acceso,
    documento_version,
    documento,
    documento_categoria,
    documento_institucional_vista,
    documento_institucional,
    curso_intento_respuesta,
    curso_intento,
    curso_inscripcion,
    curso_opcion,
    curso_pregunta,
    curso,
    tablero_tarjeta,
    tablero_columna,
    tablero,
    reaccion,
    comentario,
    encuesta_voto,
    encuesta_opcion,
    publicacion,
    reconocimiento_registro,
    perfil_evaluacion,
    perfil_hijo,
    perfil_colaborador,
    certificado_laboral,
    jefe_cargo;

-- ── Estructura organizacional del personal ───────────────────────────────────
ALTER TABLE usuario
    DROP FOREIGN KEY fk_usuario_cargo,
    DROP FOREIGN KEY fk_usuario_sede,
    DROP FOREIGN KEY fk_usuario_area,
    DROP FOREIGN KEY fk_usuario_coordinador;

-- Columna generada que solo existía para los cumpleaños del muro (V8).
DROP INDEX ix_usuario_cumpleanos ON usuario;
DROP INDEX ix_usuario_coordinador ON usuario;
ALTER TABLE usuario
    DROP COLUMN cumpleanos_mmdd,
    DROP COLUMN cargo_id,
    DROP COLUMN sede_id,
    DROP COLUMN area_id,
    DROP COLUMN coordinador_id,
    DROP COLUMN fecha_ingreso;

ALTER TABLE usuario_pendiente
    DROP FOREIGN KEY fk_pendiente_cargo,
    DROP FOREIGN KEY fk_pendiente_sede;
ALTER TABLE usuario_pendiente
    DROP COLUMN cargo_id,
    DROP COLUMN sede_id;

DROP TABLE IF EXISTS area, cargo, sede;

SET FOREIGN_KEY_CHECKS = 1;

-- ── Matriz de permisos ───────────────────────────────────────────────────────
-- Solo sobreviven la administración de usuarios y las capacidades académicas (V25).
-- rol_capacidad tiene ON DELETE CASCADE contra capacidad.
DELETE FROM capacidad
WHERE codigo NOT IN (
    'USUARIO_ADMINISTRAR',
    'CLASE_ADMINISTRAR',
    'ASISTENCIA_TOMAR',
    'ASISTENCIA_REGISTRAR',
    'ASISTENCIA_REPORTE');

UPDATE capacidad SET descripcion = 'Administrar usuarios y roles'
WHERE codigo = 'USUARIO_ADMINISTRAR';

UPDATE rol SET descripcion = 'Rectoría. Administra clases y usuarios y ve los reportes de asistencia.'
WHERE codigo = 'SUPERVISOR';
UPDATE rol SET descripcion = 'Coordinador académico. Toma asistencia en sus clases y ve sus reportes.'
WHERE codigo = 'COORDINADOR';
UPDATE rol SET descripcion = 'Docente. Toma asistencia en sus clases y ve sus reportes.'
WHERE codigo = 'USUARIO';
