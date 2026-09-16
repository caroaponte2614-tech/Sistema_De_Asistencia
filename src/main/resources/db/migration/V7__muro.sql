-- =============================================================================
-- V7 — Portal / muro (§4.7)
--
-- Publicaciones, encuestas, comentarios y reacciones. Post, encuesta y
-- reconocimiento comparten tabla y se distinguen por `tipo`: los tres son una
-- entrada del feed con autor, fecha y comentarios, y separarlos en tres tablas
-- obligaría a hacer UNION en cada página del muro.
-- =============================================================================

CREATE TABLE publicacion (
    id             CHAR(36)     NOT NULL,
    autor_id       CHAR(36)     NOT NULL,
    -- POST | ENCUESTA | RECONOCIMIENTO. Los tres del §4.7.
    tipo           VARCHAR(20)  NOT NULL DEFAULT 'POST',
    titulo         VARCHAR(200) NULL,
    cuerpo         TEXT         NOT NULL,
    -- Solo en RECONOCIMIENTO: a quién se reconoce.
    destinatario_id CHAR(36)    NULL,
    -- Publicaciones fijadas (§4.7). Ordenan por encima del resto del feed.
    fijada         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Moderación (§4.7): ADMIN y SUPERVISOR pueden ocultar. Se oculta, no se borra:
    -- borrar deja el hilo de comentarios huérfano y sin explicación.
    estado         VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    oculta_por_id  CHAR(36)     NULL,
    oculta_en      DATETIME(6)  NULL,
    -- Cierre de la encuesta. NULL = abierta indefinidamente.
    cierra_en      DATETIME(6)  NULL,
    publicado_en   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,     -- @Version (§5.2)
    PRIMARY KEY (id),
    CONSTRAINT fk_publicacion_autor        FOREIGN KEY (autor_id)        REFERENCES usuario (id),
    CONSTRAINT fk_publicacion_destinatario FOREIGN KEY (destinatario_id) REFERENCES usuario (id),
    CONSTRAINT fk_publicacion_oculta_por   FOREIGN KEY (oculta_por_id)   REFERENCES usuario (id),
    CONSTRAINT ck_publicacion_tipo   CHECK (tipo IN ('POST', 'ENCUESTA', 'RECONOCIMIENTO')),
    CONSTRAINT ck_publicacion_estado CHECK (estado IN ('VISIBLE', 'OCULTA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- El feed: fijadas primero, luego por fecha. Es la consulta paginada del §5.3.
CREATE INDEX ix_publicacion_feed ON publicacion (estado, fijada, publicado_en);

CREATE TABLE encuesta_opcion (
    id             CHAR(36)     NOT NULL,
    publicacion_id CHAR(36)     NOT NULL,
    texto          VARCHAR(200) NOT NULL,
    orden          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_encuesta_opcion_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_encuesta_opcion_orden ON encuesta_opcion (publicacion_id, orden);

CREATE TABLE encuesta_voto (
    id             CHAR(36)    NOT NULL,
    -- Se guarda la publicación además de la opción, y no es redundancia: es lo que
    -- permite la única restricción que importa aquí —un voto por persona y encuesta—.
    -- Colgando solo de la opción, alguien podría votar todas las opciones.
    publicacion_id CHAR(36)    NOT NULL,
    opcion_id      CHAR(36)    NOT NULL,
    usuario_id     CHAR(36)    NOT NULL,
    votado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_encuesta_voto UNIQUE (publicacion_id, usuario_id),
    CONSTRAINT fk_voto_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_voto_opcion      FOREIGN KEY (opcion_id)      REFERENCES encuesta_opcion (id) ON DELETE CASCADE,
    CONSTRAINT fk_voto_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_voto_opcion ON encuesta_voto (opcion_id);

CREATE TABLE comentario (
    id             CHAR(36)     NOT NULL,
    publicacion_id CHAR(36)     NOT NULL,
    autor_id       CHAR(36)     NOT NULL,
    cuerpo         VARCHAR(2000) NOT NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    oculto_por_id  CHAR(36)     NULL,
    oculto_en      DATETIME(6)  NULL,
    creado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_comentario_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_comentario_autor       FOREIGN KEY (autor_id)       REFERENCES usuario (id),
    CONSTRAINT fk_comentario_oculto_por  FOREIGN KEY (oculto_por_id)  REFERENCES usuario (id),
    CONSTRAINT ck_comentario_estado CHECK (estado IN ('VISIBLE', 'OCULTO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_comentario_publicacion ON comentario (publicacion_id, creado_en);

-- Una reacción cuelga de una publicación O de un comentario, nunca de las dos.
-- La CHECK lo garantiza; sin ella, el recuento de reacciones necesitaría filtrar.
CREATE TABLE reaccion (
    id             CHAR(36)    NOT NULL,
    publicacion_id CHAR(36)    NULL,
    comentario_id  CHAR(36)    NULL,
    usuario_id     CHAR(36)    NOT NULL,
    tipo           VARCHAR(20) NOT NULL DEFAULT 'ME_GUSTA',
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Una reacción por persona y objeto. En MySQL, un UNIQUE con columnas NULL admite
    -- repetidos de NULL, que es justo lo que hace falta: las dos parejas conviven.
    CONSTRAINT uq_reaccion_publicacion UNIQUE (publicacion_id, usuario_id),
    CONSTRAINT uq_reaccion_comentario  UNIQUE (comentario_id, usuario_id),
    CONSTRAINT fk_reaccion_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_reaccion_comentario  FOREIGN KEY (comentario_id)  REFERENCES comentario (id) ON DELETE CASCADE,
    CONSTRAINT fk_reaccion_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_reaccion_objeto CHECK (
        (publicacion_id IS NOT NULL AND comentario_id IS NULL) OR
        (publicacion_id IS NULL AND comentario_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
