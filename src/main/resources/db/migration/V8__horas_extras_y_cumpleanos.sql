-- =============================================================================
-- V8 — Horas extras (§4.6) y fecha de nacimiento
--
-- El §4.6 tiene TRES preguntas sin responder: periodicidad del corte, tipos de
-- recargo a discriminar y si debe cuadrar contra el biométrico. El esquema se
-- diseña para que las respuestas sean DATOS y no una migración nueva:
--
--   · La periodicidad no se codifica: `horas_extra_periodo` guarda fecha de inicio
--     y fin explícitas, así que quincenal, mensual o lo que decidan cabe igual.
--   · Los tipos de recargo son un catálogo. Se siembran los cuatro que NOMBRA el
--     prompt, pero con `factor` en NULL y `pendiente_confirmacion = TRUE`: los
--     porcentajes de recargo son cifras legales y no se inventan.
--   · El cuadre contra biométrico queda como una columna opcional de contraste,
--     no como una dependencia.
-- =============================================================================

CREATE TABLE recargo_tipo (
    codigo                 VARCHAR(40)  NOT NULL,
    nombre                 VARCHAR(120) NOT NULL,
    -- Multiplicador sobre la hora ordinaria. NULL = todavía sin confirmar.
    factor                 DECIMAL(4,2) NULL,
    pendiente_confirmacion BOOLEAN      NOT NULL DEFAULT FALSE,
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Los nombres salen del §4.6 ("diurna, nocturna, dominical, festiva"). Los factores
-- los confirma Talento Humano; hasta entonces la fila está marcada.
INSERT INTO recargo_tipo (codigo, nombre, factor, pendiente_confirmacion) VALUES
    ('DIURNA',    'Hora extra diurna',    NULL, TRUE),
    ('NOCTURNA',  'Hora extra nocturna',  NULL, TRUE),
    ('DOMINICAL', 'Recargo dominical',    NULL, TRUE),
    ('FESTIVA',   'Recargo festivo',      NULL, TRUE);

CREATE TABLE horas_extra_periodo (
    id           CHAR(36)     NOT NULL,
    nombre       VARCHAR(120) NOT NULL,
    fecha_inicio DATE         NOT NULL,
    fecha_fin    DATE         NOT NULL,
    -- ABIERTO: el coordinador puede diligenciar. CERRADO: solo lectura y reportes.
    estado       VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO',
    cerrado_por_id CHAR(36)   NULL,
    cerrado_en   DATETIME(6)  NULL,
    creado_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_horas_extra_periodo UNIQUE (fecha_inicio, fecha_fin),
    CONSTRAINT fk_periodo_cerrado_por FOREIGN KEY (cerrado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_periodo_estado CHECK (estado IN ('ABIERTO', 'CERRADO')),
    CONSTRAINT ck_periodo_rango  CHECK (fecha_fin >= fecha_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE horas_extra_registro (
    id                CHAR(36)     NOT NULL,
    periodo_id        CHAR(36)     NOT NULL,
    colaborador_id    CHAR(36)     NOT NULL,
    recargo_codigo    VARCHAR(40)  NOT NULL,
    fecha             DATE         NOT NULL,
    horas             DECIMAL(5,2) NOT NULL,
    observacion       VARCHAR(300) NULL,
    -- Trazabilidad de quién registró qué (§4.6). No es el colaborador: es el
    -- coordinador que diligenció el formulario.
    registrado_por_id CHAR(36)     NOT NULL,
    -- Contraste opcional con el biométrico. Se llena si algún día se conecta; que
    -- esté en NULL no impide registrar.
    horas_biometrico  DECIMAL(5,2) NULL,
    creado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- Un renglón por persona, día y tipo de recargo: evita el doble registro, que es
    -- el error clásico cuando dos coordinadores tocan el mismo periodo.
    CONSTRAINT uq_horas_extra_registro UNIQUE (periodo_id, colaborador_id, fecha, recargo_codigo),
    CONSTRAINT fk_horas_periodo     FOREIGN KEY (periodo_id)        REFERENCES horas_extra_periodo (id) ON DELETE CASCADE,
    CONSTRAINT fk_horas_colaborador FOREIGN KEY (colaborador_id)    REFERENCES usuario (id),
    CONSTRAINT fk_horas_recargo     FOREIGN KEY (recargo_codigo)    REFERENCES recargo_tipo (codigo),
    CONSTRAINT fk_horas_registrado  FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_horas_positivas CHECK (horas > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_horas_periodo_colaborador ON horas_extra_registro (periodo_id, colaborador_id);
CREATE INDEX ix_horas_colaborador_fecha   ON horas_extra_registro (colaborador_id, fecha);

-- Topes por tipo de recargo. El §4.6 pide "validación de topes" pero no dice cuáles:
-- la tabla existe vacía y el servicio no valida nada mientras no haya filas.
CREATE TABLE horas_extra_tope (
    id             CHAR(36)     NOT NULL,
    recargo_codigo VARCHAR(40)  NULL,          -- NULL = tope global, sumando todos
    ambito         VARCHAR(20)  NOT NULL,      -- DIA | SEMANA | PERIODO
    max_horas      DECIMAL(5,2) NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT fk_tope_recargo FOREIGN KEY (recargo_codigo) REFERENCES recargo_tipo (codigo) ON DELETE CASCADE,
    CONSTRAINT ck_tope_ambito CHECK (ambito IN ('DIA', 'SEMANA', 'PERIODO')),
    CONSTRAINT ck_tope_horas  CHECK (max_horas > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Cumpleaños ───────────────────────────────────────────────────────────────
-- El §4.7 pide cumpleaños en el muro y la fecha de nacimiento no existía: ninguno de
-- los tres sistemas de origen la guardaba (docs/DECISIONES.md).

ALTER TABLE usuario ADD COLUMN fecha_nacimiento DATE NULL AFTER fecha_ingreso;

-- Buscar "quién cumple hoy" es filtrar por mes y día, no por fecha completa: un
-- índice sobre `fecha_nacimiento` no serviría de nada, porque el año siempre estorba.
--
-- La solución obvia sería un índice de expresión sobre MONTH()/DAY(), pero el motor
-- de esta instalación es MariaDB 10.4 y los índices funcionales son de MySQL 8.0.13
-- en adelante. Se usa una columna generada y almacenada, que MariaDB 10.2+ y MySQL
-- 5.7+ soportan por igual, y se indexa esa. Formato MMDD: el 3 de marzo es 303.
ALTER TABLE usuario
    ADD COLUMN cumpleanos_mmdd SMALLINT
    GENERATED ALWAYS AS (MONTH(fecha_nacimiento) * 100 + DAY(fecha_nacimiento)) STORED;

CREATE INDEX ix_usuario_cumpleanos ON usuario (cumpleanos_mmdd);
