package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Foto de perfil de un usuario. Tabla aparte de {@link Usuario} a propósito: ver el
 * comentario de cabecera de la migración V9.
 */
@Entity
@Table(name = "usuario_foto")
@Getter
@Setter
public class UsuarioFoto {

    /** Mismo id que el usuario dueño: una foto por persona, sin id propio que administrar. */
    @Id
    @Column(name = "usuario_id", length = 36, nullable = false)
    private String usuarioId;

    /**
     * columnDefinition explícito: sin él, Hibernate valida un {@code byte[]} con
     * {@code @Lob} contra TINYBLOB (255 bytes, insuficiente para una foto), y el arranque
     * revienta contra el MEDIUMBLOB real de la migración V9 (ddl-auto=validate).
     */
    @Lob
    @Column(name = "contenido", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    @Column(name = "tipo_contenido", length = 100, nullable = false)
    private String tipoContenido;

    @Column(name = "actualizada_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadaEn;
}
