-- =============================================================================
-- V25 — Modelo escolar mínimo y asistencia por QR
--
-- Lo escolar se apoya en lo que ya existe y no lo duplica: el estudiante y el docente
-- son filas de `usuario` (el docente ya es el "colaborador" de todos los módulos de
-- talento humano), y la matrícula es una tabla de cruce, no otra identidad.
--
-- `curso` NO se toca: esa tabla es el motor de capacitaciones del personal (video +
-- quiz). Una materia con horario y estudiantes es otra cosa, y se llama `clase`.
--
-- Candados del antifraude que hace cumplir el ESQUEMA, no el código:
--   · una sola sesión abierta por clase          -> asistencia_sesion.clase_abierta_id UNIQUE
--   · un registro por estudiante y sesión        -> uq_asistencia_registro
--   · un dispositivo no registra a dos personas  -> uq_asistencia_registro_dispositivo
--   · un celular activo por estudiante           -> asistencia_dispositivo.activo_estudiante_id UNIQUE
--   · un celular pertenece a un solo estudiante  -> asistencia_dispositivo.activo_identificador UNIQUE
--
-- Las tres últimas columnas "activo_*" son el reemplazo de un índice parcial (D-012:
-- MariaDB 10.4 no los tiene): valen mientras la regla aplica y quedan en NULL cuando
-- deja de aplicar, y un UNIQUE admite tantos NULL como haga falta.
-- =============================================================================

-- ── Rol nuevo ────────────────────────────────────────────────────────────────
INSERT INTO rol (codigo, nombre, descripcion) VALUES
    ('ESTUDIANTE', 'Estudiante', 'Estudiante matriculado. Solo registra su propia asistencia y ve su historial.');

-- ── Clases, horarios y matrícula ─────────────────────────────────────────────

CREATE TABLE clase (
    id              CHAR(36)     NOT NULL,
    nombre          VARCHAR(150) NOT NULL,          -- "Matemáticas 7A"
    asignatura      VARCHAR(120) NOT NULL,
    grupo           VARCHAR(40)  NULL,              -- "7A"
    periodo         VARCHAR(20)  NOT NULL,          -- "2026-2"
    -- El docente a cargo: es quien abre y cierra las sesiones de asistencia.
    profesor_id     CHAR(36)     NOT NULL,
    activa          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_clase_profesor FOREIGN KEY (profesor_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_clase_profesor ON clase (profesor_id, activa);
CREATE INDEX ix_clase_periodo  ON clase (periodo, activa);

-- Franja semanal de una clase. Sirve para dos cosas: mostrar el horario y, al abrir la
-- sesión, saber a qué hora "empezaba" la clase para medir la tardanza.
CREATE TABLE clase_horario (
    id          CHAR(36)    NOT NULL,
    clase_id    CHAR(36)    NOT NULL,
    dia_semana  VARCHAR(10) NOT NULL,   -- MONDAY..SUNDAY (java.time.DayOfWeek)
    hora_inicio TIME        NOT NULL,
    hora_fin    TIME        NOT NULL,
    aula        VARCHAR(60) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_clase_horario_clase FOREIGN KEY (clase_id) REFERENCES clase (id) ON DELETE CASCADE,
    CONSTRAINT ck_clase_horario_dia CHECK (dia_semana IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT ck_clase_horario_rango CHECK (hora_fin > hora_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_clase_horario_clase ON clase_horario (clase_id, dia_semana);

-- Matrícula. Baja lógica (activa = FALSE) y no borrado: los registros de asistencia
-- viejos tienen que seguir teniendo sentido.
CREATE TABLE clase_inscripcion (
    id            CHAR(36)    NOT NULL,
    clase_id      CHAR(36)    NOT NULL,
    estudiante_id CHAR(36)    NOT NULL,
    activa        BOOLEAN     NOT NULL DEFAULT TRUE,
    inscrito_en   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_clase_inscripcion UNIQUE (clase_id, estudiante_id),
    CONSTRAINT fk_clase_inscripcion_clase      FOREIGN KEY (clase_id)      REFERENCES clase (id) ON DELETE CASCADE,
    CONSTRAINT fk_clase_inscripcion_estudiante FOREIGN KEY (estudiante_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_clase_inscripcion_estudiante ON clase_inscripcion (estudiante_id, activa);

-- ── Asistencia ───────────────────────────────────────────────────────────────

CREATE TABLE asistencia_sesion (
    id                 CHAR(36)    NOT NULL,
    clase_id           CHAR(36)    NOT NULL,
    -- Quién la abrió. Normalmente el profesor de la clase; un ADMIN también puede.
    profesor_id        CHAR(36)    NOT NULL,
    -- Franja del horario con la que se abrió, si el profesor eligió una. NULL = clase
    -- fuera de horario (refuerzo, recuperación), y entonces la hora de referencia es la
    -- de apertura.
    horario_id         CHAR(36)    NULL,
    estado             VARCHAR(10) NOT NULL DEFAULT 'ABIERTA',
    abierta_en         DATETIME(6) NOT NULL,
    cerrada_en         DATETIME(6) NULL,
    -- Desde cuándo se cuenta la tardanza. Se congela al abrir.
    hora_referencia    DATETIME(6) NOT NULL,
    tolerancia_minutos INT         NOT NULL,
    -- Vale clase_id mientras la sesión está abierta y NULL cuando se cierra: el UNIQUE
    -- es lo que impide dos sesiones abiertas de la misma clase a la vez.
    clase_abierta_id   CHAR(36)    NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_asistencia_sesion_abierta UNIQUE (clase_abierta_id),
    CONSTRAINT fk_asistencia_sesion_clase    FOREIGN KEY (clase_id)    REFERENCES clase (id),
    CONSTRAINT fk_asistencia_sesion_profesor FOREIGN KEY (profesor_id) REFERENCES usuario (id),
    CONSTRAINT fk_asistencia_sesion_horario  FOREIGN KEY (horario_id)  REFERENCES clase_horario (id) ON DELETE SET NULL,
    CONSTRAINT ck_asistencia_sesion_estado CHECK (estado IN ('ABIERTA', 'CERRADA')),
    CONSTRAINT ck_asistencia_sesion_tolerancia CHECK (tolerancia_minutos BETWEEN 0 AND 120),
    CONSTRAINT ck_asistencia_sesion_abierta CHECK (clase_abierta_id IS NULL OR clase_abierta_id = clase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_asistencia_sesion_clase ON asistencia_sesion (clase_id, abierta_en);

-- Celular vinculado a un estudiante. El identificador es una cookie aleatoria que pone
-- el servidor (HttpOnly): en la tabla se guarda solo su SHA-256, nunca el valor.
CREATE TABLE asistencia_dispositivo (
    id                    CHAR(36)     NOT NULL,
    estudiante_id         CHAR(36)     NOT NULL,
    identificador_hash    CHAR(64)     NOT NULL,
    descripcion           VARCHAR(255) NULL,          -- user agent resumido, para que se reconozca
    estado                VARCHAR(12)  NOT NULL,      -- PENDIENTE | ACTIVO | REEMPLAZADO | RECHAZADO | REVOCADO
    solicitado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    vinculado_en          DATETIME(6)  NULL,
    resuelto_por_id       CHAR(36)     NULL,          -- profesor o admin que aprobó/rechazó el cambio
    resuelto_en           DATETIME(6)  NULL,
    -- Los dos candados del antifraude, explicados en la cabecera.
    activo_estudiante_id  CHAR(36)     NULL,
    activo_identificador  CHAR(64)     NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_dispositivo_activo_estudiante  UNIQUE (activo_estudiante_id),
    CONSTRAINT uq_dispositivo_activo_identificador UNIQUE (activo_identificador),
    CONSTRAINT fk_dispositivo_estudiante  FOREIGN KEY (estudiante_id)   REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_dispositivo_resuelto_por FOREIGN KEY (resuelto_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_dispositivo_estado CHECK (estado IN
        ('PENDIENTE', 'ACTIVO', 'REEMPLAZADO', 'RECHAZADO', 'REVOCADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_dispositivo_estudiante   ON asistencia_dispositivo (estudiante_id, estado);
CREATE INDEX ix_dispositivo_identificador ON asistencia_dispositivo (identificador_hash);

CREATE TABLE asistencia_registro (
    id                CHAR(36)      NOT NULL,
    sesion_id         CHAR(36)      NOT NULL,
    estudiante_id     CHAR(36)      NOT NULL,
    estado            VARCHAR(12)   NOT NULL,   -- PRESENTE | TARDE | AUSENTE | JUSTIFICADO
    -- Cómo llegó: QR escaneado, código de 6 dígitos, mano del profesor, o CIERRE para el
    -- ausente que genera el sistema al cerrar la sesión.
    metodo            VARCHAR(10)   NOT NULL,
    registrado_en     DATETIME(6)   NOT NULL,
    -- Auditoría del registro (§ antifraude). NULL en los registros manuales.
    dispositivo_id    CHAR(36)      NULL,
    ip                VARCHAR(45)   NULL,
    user_agent        VARCHAR(255)  NULL,
    latitud           DECIMAL(9,6)  NULL,
    longitud          DECIMAL(9,6)  NULL,
    observacion       VARCHAR(500)  NULL,       -- motivo de la justificación o de la corrección
    modificado_por_id CHAR(36)      NULL,
    modificado_en     DATETIME(6)   NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_asistencia_registro             UNIQUE (sesion_id, estudiante_id),
    CONSTRAINT uq_asistencia_registro_dispositivo UNIQUE (sesion_id, dispositivo_id),
    CONSTRAINT fk_asistencia_registro_sesion      FOREIGN KEY (sesion_id)         REFERENCES asistencia_sesion (id) ON DELETE CASCADE,
    CONSTRAINT fk_asistencia_registro_estudiante  FOREIGN KEY (estudiante_id)     REFERENCES usuario (id),
    CONSTRAINT fk_asistencia_registro_dispositivo FOREIGN KEY (dispositivo_id)    REFERENCES asistencia_dispositivo (id),
    CONSTRAINT fk_asistencia_registro_modificado  FOREIGN KEY (modificado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_asistencia_registro_estado CHECK (estado IN
        ('PRESENTE', 'TARDE', 'AUSENTE', 'JUSTIFICADO')),
    CONSTRAINT ck_asistencia_registro_metodo CHECK (metodo IN
        ('QR', 'CODIGO', 'MANUAL', 'CIERRE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_asistencia_registro_estudiante ON asistencia_registro (estudiante_id, registrado_en);

-- ── Matriz de permisos ───────────────────────────────────────────────────────
-- El ámbito PROPIO de ASISTENCIA_TOMAR significa "las clases que yo dicto"; TODOS, las
-- de cualquiera. Por eso rectoría (SUPERVISOR) administra clases y ve reportes de todo,
-- pero solo abre sesiones de las clases que tenga a su nombre: abrir y cerrar es del
-- profesor de la clase (o de un ADMIN).

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('CLASE_ADMINISTRAR',    'Crear clases, horarios y matricular estudiantes',                'ASISTENCIA'),
    ('ASISTENCIA_TOMAR',     'Abrir y cerrar sesiones, corregir registros y autorizar celulares', 'ASISTENCIA'),
    ('ASISTENCIA_REGISTRAR', 'Registrar la propia asistencia con QR o código',                 'ASISTENCIA'),
    ('ASISTENCIA_REPORTE',   'Ver y exportar reportes de asistencia',                          'ASISTENCIA');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('ADMIN',       'CLASE_ADMINISTRAR',    'TODOS',  TRUE, FALSE),
    ('ADMIN',       'ASISTENCIA_TOMAR',     'TODOS',  TRUE, FALSE),
    ('ADMIN',       'ASISTENCIA_REPORTE',   'TODOS',  TRUE, FALSE),

    ('SUPERVISOR',  'CLASE_ADMINISTRAR',    'TODOS',  TRUE, FALSE),
    ('SUPERVISOR',  'ASISTENCIA_TOMAR',     'PROPIO', TRUE, FALSE),
    ('SUPERVISOR',  'ASISTENCIA_REPORTE',   'TODOS',  TRUE, FALSE),

    ('COORDINADOR', 'ASISTENCIA_TOMAR',     'PROPIO', TRUE, FALSE),
    ('COORDINADOR', 'ASISTENCIA_REPORTE',   'PROPIO', TRUE, FALSE),

    ('USUARIO',     'ASISTENCIA_TOMAR',     'PROPIO', TRUE, FALSE),
    ('USUARIO',     'ASISTENCIA_REPORTE',   'PROPIO', TRUE, FALSE),

    ('ESTUDIANTE',  'ASISTENCIA_REGISTRAR', 'PROPIO', TRUE, FALSE);
