-- =============================================================================
-- V4 — Solicitudes y motor de aprobación (§4.4 y §4.5)
--
-- Decisión de fondo: UNA sola maquinaria de aprobación, parametrizada por tipo de
-- solicitud. El §4.5 lo pide con todas las letras ("una sola implementación de
-- workflow parametrizada por tipo de solicitud, no dos copias"), así que permisos y
-- vacaciones comparten tabla, estados e historial. Lo que cambia entre unos y otros
-- vive en `solicitud_tipo`, que es un catálogo: añadir un tipo nuevo de permiso es
-- un INSERT, no una migración.
--
-- Los estados son EXACTAMENTE los seis del diagrama del §4.4. No se añade ninguno
-- (no hay CANCELADO ni EN_REVISION): lo que el prompt no dibuja, no se inventa.
-- =============================================================================

-- ── Catálogo de tipos ────────────────────────────────────────────────────────

CREATE TABLE solicitud_tipo (
    codigo                  VARCHAR(40)  NOT NULL,
    nombre                  VARCHAR(120) NOT NULL,
    -- PERMISO o VACACIONES. Separa las dos familias sin duplicar el flujo.
    familia                 VARCHAR(20)  NOT NULL,
    -- TRUE solo en vacaciones: es lo que dispara el cálculo de saldo (§4.5).
    descuenta_saldo         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Luto y calamidad suelen exigir soporte. Queda como dato para que Talento
    -- Humano lo cambie sin tocar código.
    requiere_soporte        BOOLEAN      NOT NULL DEFAULT FALSE,
    activo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (codigo),
    CONSTRAINT ck_solicitud_tipo_familia CHECK (familia IN ('PERMISO', 'VACACIONES'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Los cinco tipos de permiso del §4.4 más vacaciones (§4.5).
INSERT INTO solicitud_tipo (codigo, nombre, familia, descuenta_saldo, requiere_soporte) VALUES
    ('REMUNERADO',     'Permiso remunerado',     'PERMISO',    FALSE, FALSE),
    ('NO_REMUNERADO',  'Permiso no remunerado',  'PERMISO',    FALSE, FALSE),
    ('LUTO',           'Licencia por luto',      'PERMISO',    FALSE, TRUE),
    ('CALAMIDAD',      'Calamidad doméstica',    'PERMISO',    FALSE, TRUE),
    ('CUMPLEANOS',     'Día de cumpleaños',      'PERMISO',    FALSE, FALSE),
    ('VACACIONES',     'Vacaciones',             'VACACIONES', TRUE,  FALSE);

-- ── Solicitud ────────────────────────────────────────────────────────────────

CREATE TABLE solicitud (
    id                      CHAR(36)     NOT NULL,
    tipo_codigo             VARCHAR(40)  NOT NULL,
    solicitante_id          CHAR(36)     NOT NULL,

    estado                  VARCHAR(30)  NOT NULL DEFAULT 'BORRADOR',

    fecha_inicio            DATE         NOT NULL,
    fecha_fin               DATE         NOT NULL,
    -- Días hábiles del rango. DECIMAL porque hay permisos de medio día.
    dias_habiles            DECIMAL(5,2) NOT NULL,
    motivo                  VARCHAR(500) NULL,

    -- A quién le toca aprobar en primera instancia. Se resuelve AL CREAR y se guarda,
    -- no se recalcula: si la persona cambia de área a mitad de trámite, la solicitud
    -- sigue en la bandeja de quien la tenía (§4.4, "el coordinador del área del
    -- solicitante"). Es NULL cuando el propio solicitante es coordinador y la
    -- solicitud entra directo a Talento Humano.
    coordinador_asignado_id CHAR(36)     NULL,

    decidido_coord_por_id   CHAR(36)     NULL,
    decidido_coord_en       DATETIME(6)  NULL,
    decidido_rrhh_por_id    CHAR(36)     NULL,
    decidido_rrhh_en        DATETIME(6)  NULL,

    -- Idempotencia de las aprobaciones (§5.2): el doble clic del coordinador trae la
    -- misma clave y la segunda vez no hace nada.
    idempotency_key         VARCHAR(80)  NULL,

    creado_en               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                 BIGINT       NOT NULL DEFAULT 0,   -- @Version (§5.2)

    PRIMARY KEY (id),
    CONSTRAINT uq_solicitud_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_solicitud_tipo         FOREIGN KEY (tipo_codigo)             REFERENCES solicitud_tipo (codigo),
    CONSTRAINT fk_solicitud_solicitante  FOREIGN KEY (solicitante_id)          REFERENCES usuario (id),
    CONSTRAINT fk_solicitud_coordinador  FOREIGN KEY (coordinador_asignado_id) REFERENCES usuario (id),
    CONSTRAINT fk_solicitud_dec_coord    FOREIGN KEY (decidido_coord_por_id)   REFERENCES usuario (id),
    CONSTRAINT fk_solicitud_dec_rrhh     FOREIGN KEY (decidido_rrhh_por_id)    REFERENCES usuario (id),

    -- Los seis estados del diagrama del §4.4, ni uno más.
    CONSTRAINT ck_solicitud_estado CHECK (estado IN (
        'BORRADOR', 'PENDIENTE_COORDINADOR', 'PENDIENTE_SUPERVISOR',
        'APROBADO', 'RECHAZADO_COORD', 'RECHAZADO_RRHH')),
    CONSTRAINT ck_solicitud_rango CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_solicitud_dias  CHECK (dias_habiles > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bandeja del solicitante y bandejas de aprobación: los tres listados que existen.
CREATE INDEX ix_solicitud_solicitante ON solicitud (solicitante_id, estado, fecha_inicio);
CREATE INDEX ix_solicitud_bandeja     ON solicitud (estado, coordinador_asignado_id);
-- Cruces de fechas dentro del mismo equipo (§4.5: advertencia, no bloqueo).
CREATE INDEX ix_solicitud_fechas      ON solicitud (fecha_inicio, fecha_fin);

-- ── Historial inmutable ──────────────────────────────────────────────────────
-- §4.4: "Toda transición se registra... quién, cuándo, de qué estado a cuál,
-- comentario. Inmutable." Se inserta y no se toca nunca más; por eso no lleva
-- version ni actualizado_en.

CREATE TABLE solicitud_historial (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    solicitud_id   CHAR(36)     NOT NULL,
    estado_origen  VARCHAR(30)  NULL,          -- NULL en la creación
    estado_destino VARCHAR(30)  NOT NULL,
    actor_id       CHAR(36)     NULL,          -- NULL si lo movió el sistema
    -- Obligatorio en las denegaciones (§4.4). Lo valida el servicio, no la tabla:
    -- una CHECK aquí no puede mirar el estado de la fila padre.
    comentario     VARCHAR(500) NULL,
    ocurrido_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_historial_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud (id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_actor     FOREIGN KEY (actor_id)     REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_historial_solicitud ON solicitud_historial (solicitud_id, ocurrido_en);

-- ── Saldo de vacaciones ──────────────────────────────────────────────────────
-- §4.5: causados vs. tomados vs. en trámite.
--
-- "En trámite" es una columna y no un cálculo al vuelo a propósito: sin ella, dos
-- solicitudes simultáneas que quepan por separado pero no juntas pasarían las dos.
-- Es la fila sobre la que se toma el bloqueo PESIMISTA que autoriza el §5.2.

CREATE TABLE vacaciones_saldo (
    id             CHAR(36)     NOT NULL,
    usuario_id     CHAR(36)     NOT NULL,
    -- Año de causación. Las vacaciones no tomadas se acumulan por periodo.
    periodo        SMALLINT     NOT NULL,
    dias_causados  DECIMAL(6,2) NOT NULL DEFAULT 0,
    dias_tomados   DECIMAL(6,2) NOT NULL DEFAULT 0,
    dias_en_tramite DECIMAL(6,2) NOT NULL DEFAULT 0,
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_vacaciones_saldo UNIQUE (usuario_id, periodo),
    CONSTRAINT fk_vacaciones_saldo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_vacaciones_no_negativo CHECK (
        dias_causados >= 0 AND dias_tomados >= 0 AND dias_en_tramite >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cada suma y cada resta al saldo deja rastro. Sin esto, un saldo raro no se puede
-- explicar y la única salida es recalcular a mano.
CREATE TABLE vacaciones_movimiento (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    saldo_id      CHAR(36)     NOT NULL,
    -- CAUSACION | RESERVA | CONSUMO | REVERSA | AJUSTE
    tipo          VARCHAR(20)  NOT NULL,
    dias          DECIMAL(6,2) NOT NULL,
    solicitud_id  CHAR(36)     NULL,
    motivo        VARCHAR(255) NULL,
    registrado_por_id CHAR(36) NULL,
    ocurrido_en   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_vac_mov_saldo      FOREIGN KEY (saldo_id)     REFERENCES vacaciones_saldo (id) ON DELETE CASCADE,
    CONSTRAINT fk_vac_mov_solicitud  FOREIGN KEY (solicitud_id) REFERENCES solicitud (id),
    CONSTRAINT fk_vac_mov_registrado FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_vac_mov_tipo CHECK (tipo IN ('CAUSACION', 'RESERVA', 'CONSUMO', 'REVERSA', 'AJUSTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_vac_mov_saldo ON vacaciones_movimiento (saldo_id, ocurrido_en);

-- OJO: la regla de causación legal (Colombia: 15 días hábiles por año) NO se codifica
-- todavía. El §4.5 exige preguntarla antes, y el esquema no la prejuzga: `dias_causados`
-- es un número que alimenta un job o un ajuste manual, sea cual sea la regla.
