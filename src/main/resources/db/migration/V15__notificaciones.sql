-- =============================================================================
-- V15 — Notificaciones con campanita y WebSocket
--
-- Pedido del usuario (2026-09-04): "agrega notificaciones con campanita y websockets".
-- Alcance acordado: solicitudes (pendiente/decidida), muro (comentario/reacción sobre
-- lo tuyo), documentos (expediente restringido nuevo) y tablero (vencimiento).
--
-- Es una tabla y no solo un push efímero por WebSocket: si la persona no tiene la
-- pestaña abierta cuando ocurre el evento, la campanita tiene que mostrárselo igual la
-- próxima vez que entre. El WebSocket empuja en vivo lo que ya se guardó aquí; nunca es
-- la única copia.
-- =============================================================================

CREATE TABLE notificacion (
    id         CHAR(36)     NOT NULL,
    usuario_id CHAR(36)     NOT NULL,
    tipo       VARCHAR(30)  NOT NULL,
    titulo     VARCHAR(200) NOT NULL,
    cuerpo     VARCHAR(500) NULL,
    -- Ruta relativa a donde lleva hacer clic en la notificación. NULL = no navega.
    enlace     VARCHAR(300) NULL,
    leida      BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La campanita siempre pregunta "las mías, más nuevas primero" y "cuántas sin leer":
-- ambas caen sobre el mismo (usuario_id, leida, creado_en).
CREATE INDEX ix_notificacion_usuario ON notificacion (usuario_id, leida, creado_en DESC);
