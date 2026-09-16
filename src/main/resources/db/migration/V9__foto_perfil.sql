-- =============================================================================
-- V9 — Foto de perfil
--
-- Tabla aparte y no una columna BLOB en `usuario`: UsuarioDetailsService carga un
-- Usuario completo en cada login, y el panel lo vuelve a cargar en cada vista
-- (buscarConSedeYCargoPorEmail). Si la foto viviera en esa misma fila, cada una de
-- esas consultas arrastraría los bytes de la imagen aunque nadie la vaya a pintar.
-- Separada, solo se lee cuando de verdad hace falta (la página "Mi cuenta" y el
-- endpoint que sirve la imagen).
--
-- Guardada en la base de datos y no en almacenamiento de archivos: decisión
-- del usuario (2026-08-28) para un proyecto de máximo 100 usuarios síncronos — la
-- complejidad de un almacenamiento aparte no se justifica a esa escala.
-- =============================================================================

CREATE TABLE usuario_foto (
    usuario_id      CHAR(36)     NOT NULL,
    contenido       MEDIUMBLOB   NOT NULL,
    tipo_contenido  VARCHAR(100) NOT NULL,
    actualizada_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_usuario_foto_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
