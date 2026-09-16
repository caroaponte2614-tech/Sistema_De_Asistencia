-- =============================================================================
-- V17 — Capacitaciones: motor de cursos (video + quiz), base de tres requerimientos
--
-- Un solo motor cubre "carrusel de cursos disponibles", "crear cursos, subir videos,
-- crear formularios" e "inducción" — la inducción no es un modelo aparte, es un curso
-- con tipo = INDUCCION y obligatorio = TRUE. No hay tabla de "lecciones": un curso es
-- un video más un quiz, sin secuencia de varios videos (si hace falta más adelante,
-- se agrega curso_leccion entre curso y curso_pregunta sin romper esto).
-- =============================================================================

CREATE TABLE curso (
    id                  CHAR(36)     NOT NULL,
    titulo              VARCHAR(200) NOT NULL,
    descripcion         VARCHAR(1000) NULL,
    tipo                VARCHAR(20)  NOT NULL DEFAULT 'CAPACITACION',
    obligatorio         BOOLEAN      NOT NULL DEFAULT FALSE,
    video_clave         VARCHAR(500) NOT NULL,
    video_mime          VARCHAR(100) NULL,
    video_tamano_bytes  BIGINT       NULL,
    miniatura_clave     VARCHAR(500) NULL,
    publicado           BOOLEAN      NOT NULL DEFAULT FALSE,
    orden               INT          NULL,
    creado_por_id       CHAR(36)     NOT NULL,
    creado_en           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_curso_creado_por FOREIGN KEY (creado_por_id) REFERENCES usuario (id),
    CONSTRAINT ck_curso_tipo CHECK (tipo IN ('CAPACITACION', 'INDUCCION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_curso_publicado_tipo ON curso (publicado, tipo);

CREATE TABLE curso_pregunta (
    id         CHAR(36)     NOT NULL,
    curso_id   CHAR(36)     NOT NULL,
    enunciado  VARCHAR(500) NOT NULL,
    tipo       VARCHAR(20)  NOT NULL,
    orden      INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_curso_pregunta_curso FOREIGN KEY (curso_id) REFERENCES curso (id) ON DELETE CASCADE,
    CONSTRAINT ck_curso_pregunta_tipo CHECK (tipo IN ('OPCION_UNICA', 'VERDADERO_FALSO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_curso_pregunta_curso ON curso_pregunta (curso_id);

CREATE TABLE curso_opcion (
    id          CHAR(36)     NOT NULL,
    pregunta_id CHAR(36)     NOT NULL,
    texto       VARCHAR(300) NOT NULL,
    correcta    BOOLEAN      NOT NULL DEFAULT FALSE,
    orden       INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_curso_opcion_pregunta FOREIGN KEY (pregunta_id) REFERENCES curso_pregunta (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_curso_opcion_pregunta ON curso_opcion (pregunta_id);

CREATE TABLE curso_inscripcion (
    id                   CHAR(36)     NOT NULL,
    curso_id             CHAR(36)     NOT NULL,
    usuario_id           CHAR(36)     NOT NULL,
    video_completado_en  DATETIME(6)  NULL,
    quiz_aprobado        BOOLEAN      NOT NULL DEFAULT FALSE,
    mejor_nota           DECIMAL(5,2) NULL,
    intentos             INT          NOT NULL DEFAULT 0,
    completado_en        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_curso_inscripcion UNIQUE (curso_id, usuario_id),
    CONSTRAINT fk_curso_inscripcion_curso   FOREIGN KEY (curso_id)   REFERENCES curso (id),
    CONSTRAINT fk_curso_inscripcion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un intento por cada vez que alguien presenta el quiz; curso_inscripcion guarda solo
-- el resumen (mejor nota, aprobado). El detalle de qué contestó vive acá.
CREATE TABLE curso_intento (
    id             CHAR(36)     NOT NULL,
    inscripcion_id CHAR(36)     NOT NULL,
    iniciado_en    DATETIME(6)  NOT NULL,
    terminado_en   DATETIME(6)  NULL,
    nota           DECIMAL(5,2) NULL,
    aprobado       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_curso_intento_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES curso_inscripcion (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_curso_intento_inscripcion ON curso_intento (inscripcion_id);

-- Respuesta elegida por pregunta en un intento. CASCADE también contra curso_pregunta y
-- curso_opcion a propósito: si Talento Humano reemplaza el cuestionario de un curso ya
-- tomado, el detalle pregunta-a-pregunta de los intentos viejos se pierde (referenciaba
-- preguntas que ya no existen), pero el resumen en curso_intento (nota, aprobado) queda
-- intacto — es el costo aceptado de "borrar y recrear el quiz" en vez de versionarlo.
CREATE TABLE curso_intento_respuesta (
    id                CHAR(36)  NOT NULL,
    intento_id        CHAR(36)  NOT NULL,
    pregunta_id       CHAR(36)  NOT NULL,
    opcion_elegida_id CHAR(36)  NULL,
    correcta          BOOLEAN   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_intento_respuesta_intento FOREIGN KEY (intento_id)        REFERENCES curso_intento (id)  ON DELETE CASCADE,
    CONSTRAINT fk_intento_respuesta_pregunta FOREIGN KEY (pregunta_id)      REFERENCES curso_pregunta (id) ON DELETE CASCADE,
    CONSTRAINT fk_intento_respuesta_opcion  FOREIGN KEY (opcion_elegida_id) REFERENCES curso_opcion (id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_intento_respuesta_intento ON curso_intento_respuesta (intento_id);

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('CAPACITACION_VER',          'Ver el catálogo de cursos y tomarlos',    'CAPACITACION'),
    ('CAPACITACION_ADMINISTRAR',  'Crear, editar y publicar cursos',         'CAPACITACION');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('USUARIO',     'CAPACITACION_VER',         'PROPIO', TRUE, FALSE),
    ('COORDINADOR', 'CAPACITACION_VER',         'PROPIO', TRUE, FALSE),
    ('SUPERVISOR',  'CAPACITACION_VER',         'TODOS',  TRUE, FALSE),
    ('SUPERVISOR',  'CAPACITACION_ADMINISTRAR', 'TODOS',  TRUE, FALSE),
    ('ADMIN',       'CAPACITACION_VER',         'TODOS',  TRUE, FALSE),
    ('ADMIN',       'CAPACITACION_ADMINISTRAR', 'TODOS',  TRUE, FALSE);
