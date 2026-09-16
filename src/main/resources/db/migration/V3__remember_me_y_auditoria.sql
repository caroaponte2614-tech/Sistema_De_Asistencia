-- =============================================================================
-- V3 — "Mantener sesión activa" y afinado de la bitácora
--
-- Notas de diseño:
--   · persistent_logins es la tabla que espera JdbcTokenRepositoryImpl de Spring
--     Security, con SUS nombres de columna: no se traducen al castellano ni se
--     cambian los tipos, o el repositorio deja de encontrarla.
--     El único ajuste es username VARCHAR(180) en vez de 64, para que quepa un
--     correo del mismo largo que usuario.email.
--   · Se deja que la cree Flyway y no la propia clase (setCreateTableOnStartup
--     queda en false): cero DDL fuera de migraciones (§5.1).
--   · El índice por IP lo pide el límite de intentos, que cuenta filas de
--     auditoria_acceso por IP y evento dentro de una ventana de tiempo. Sin él,
--     cada login haría un recorrido completo de la tabla.
-- =============================================================================

CREATE TABLE persistent_logins (
    username  VARCHAR(180) NOT NULL,
    series    VARCHAR(64)  NOT NULL,
    token     VARCHAR(64)  NOT NULL,
    last_used TIMESTAMP    NOT NULL,
    PRIMARY KEY (series)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_persistent_logins_username ON persistent_logins (username);

-- Sostiene countByIpAndEventoAndOcurridoEnAfter.
CREATE INDEX ix_auditoria_ip_evento ON auditoria_acceso (ip, evento, ocurrido_en);

-- El índice de V1 era (email_intentado, ocurrido_en); el conteo filtra además por
-- evento, así que se sustituye por uno que cubra las tres columnas.
DROP INDEX ix_auditoria_email ON auditoria_acceso;
CREATE INDEX ix_auditoria_email_evento ON auditoria_acceso (email_intentado, evento, ocurrido_en);
