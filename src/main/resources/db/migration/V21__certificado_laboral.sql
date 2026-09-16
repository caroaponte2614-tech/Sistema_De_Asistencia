-- =============================================================================
-- V21 — Certificado laboral: generación automática de PDF
--
-- Sin aprobación: todo lo que se imprime (nombre, cédula, cargo, fecha de ingreso) ya
-- es un hecho autoritativo en `usuario`, así que una revisión manual solo agregaría
-- demora sin agregar integridad. Igual queda este registro de emisión como trazabilidad
-- de quién lo pidió, cuándo, y su código de verificación — no es una máquina de
-- estados: si el negocio pide después un paso de revisión real, se agrega una columna
-- `estado` sin romper filas existentes.
-- =============================================================================

CREATE TABLE certificado_laboral (
    id                    CHAR(36)     NOT NULL,
    usuario_id            CHAR(36)     NOT NULL,
    solicitado_por_id     CHAR(36)     NOT NULL,
    codigo_verificacion   VARCHAR(20)  NOT NULL,
    pdf_clave             VARCHAR(500) NOT NULL,
    emitido_en            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_certificado_codigo    UNIQUE (codigo_verificacion),
    CONSTRAINT fk_certificado_usuario   FOREIGN KEY (usuario_id)        REFERENCES usuario (id),
    CONSTRAINT fk_certificado_solicitado FOREIGN KEY (solicitado_por_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_certificado_usuario ON certificado_laboral (usuario_id, emitido_en);

INSERT INTO capacidad (codigo, descripcion, modulo) VALUES
    ('CERTIFICADO_LABORAL_SOLICITAR',         'Generar el propio certificado laboral',        'CERTIFICADO_LABORAL'),
    ('CERTIFICADO_LABORAL_GENERAR_TERCEROS',  'Generar el certificado laboral de otra persona', 'CERTIFICADO_LABORAL');

INSERT INTO rol_capacidad (rol_codigo, capacidad_codigo, ambito, habilitado, pendiente_confirmacion) VALUES
    ('USUARIO',     'CERTIFICADO_LABORAL_SOLICITAR',        'PROPIO', TRUE, FALSE),
    ('COORDINADOR', 'CERTIFICADO_LABORAL_SOLICITAR',        'PROPIO', TRUE, FALSE),
    ('SUPERVISOR',  'CERTIFICADO_LABORAL_SOLICITAR',        'PROPIO', TRUE, FALSE),
    ('SUPERVISOR',  'CERTIFICADO_LABORAL_GENERAR_TERCEROS',  'TODOS',  TRUE, FALSE),
    ('ADMIN',       'CERTIFICADO_LABORAL_SOLICITAR',        'PROPIO', TRUE, FALSE),
    ('ADMIN',       'CERTIFICADO_LABORAL_GENERAR_TERCEROS',  'TODOS',  TRUE, FALSE);
