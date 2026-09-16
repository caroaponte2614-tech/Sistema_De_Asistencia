-- =============================================================================
-- V6 — Tablero de tareas (§4.3)
--
-- Origen del modelo: un proyecto de referencia (model/TareaTablero.java) + ColumnaTablero.java
-- + PrioridadTarea.java. Se conservan sus cuatro columnas (Pendiente, En curso,
-- Esperando respuesta, Hecho), sus dos prioridades y la etiqueta libre de área.
--
-- Tres diferencias con el original, todas exigidas por el prompt:
--   1. Allí el tablero era UNO, exclusivo del rol ADMIN. Aquí cada usuario tiene el
--      suyo (§4.3), así que aparece la tabla `tablero`.
--   2. El original NO persistía el orden: ordenaba en memoria por prioridad y fecha
--      (TableroService.java:47). Con drag & drop eso significa que arrastrar una
--      tarjeta no se guarda. Se añade `orden`.
--   3. Bloqueo optimista con `version` (§5.2): dos navegadores sobre la misma
--      tarjeta es el caso normal cuando los movimientos van por WebSocket.
-- =============================================================================

CREATE TABLE tablero (
    id         CHAR(36)     NOT NULL,
    -- Un tablero por persona. La restricción única es lo que hace cumplir "cada
    -- usuario tiene el suyo" sin depender del código.
    usuario_id CHAR(36)     NOT NULL,
    nombre     VARCHAR(120) NOT NULL DEFAULT 'Mi tablero',
    creado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_tablero_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_tablero_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Las columnas son filas y no un enum: el original las tenía fijas en código y
-- cambiar una etiqueta obligaba a recompilar. Cada tablero nace con las cuatro
-- heredadas, pero puede añadir las suyas.
CREATE TABLE tablero_columna (
    id         CHAR(36)     NOT NULL,
    tablero_id CHAR(36)     NOT NULL,
    codigo     VARCHAR(30)  NOT NULL,   -- PENDIENTE | CURSO | ESPERA | HECHO
    nombre     VARCHAR(80)  NOT NULL,
    orden      INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tablero_columna_codigo UNIQUE (tablero_id, codigo),
    CONSTRAINT fk_tablero_columna_tablero FOREIGN KEY (tablero_id) REFERENCES tablero (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_tablero_columna_orden ON tablero_columna (tablero_id, orden);

CREATE TABLE tablero_tarjeta (
    id             CHAR(36)     NOT NULL,
    columna_id     CHAR(36)     NOT NULL,
    titulo         VARCHAR(200) NOT NULL,
    -- `area` en el original: etiqueta libre de área o proyecto.
    etiqueta       VARCHAR(120) NULL,
    nota           TEXT         NULL,
    prioridad      VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    -- Lo que le faltaba al original. Posición dentro de la columna: es lo que hace
    -- que arrastrar una tarjeta sobreviva a recargar la página.
    orden          INT          NOT NULL DEFAULT 0,
    creado_por_id  CHAR(36)     NOT NULL,
    creado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_tarjeta_columna FOREIGN KEY (columna_id)    REFERENCES tablero_columna (id) ON DELETE CASCADE,
    CONSTRAINT fk_tarjeta_creador FOREIGN KEY (creado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_tarjeta_prioridad CHECK (prioridad IN ('ALTA', 'NORMAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_tarjeta_columna_orden ON tablero_tarjeta (columna_id, orden);
