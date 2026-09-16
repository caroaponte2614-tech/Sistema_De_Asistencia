package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Identificación estructurada de un colaborador: lo que hoy solo llena el supervisor
 * (contacto de emergencia). Los datos repetibles —hijos, evaluaciones— viven en tablas
 * aparte ({@link PerfilHijo}, {@link PerfilEvaluacion}), no acá.
 *
 * <p>La PK es el propio {@code usuario_id}: como mucho hay una fila por persona, no
 * hace falta un id propio.</p>
 */
@Entity
@Table(name = "perfil_colaborador")
@Getter
@Setter
public class PerfilColaborador {

    @Id
    @Column(name = "usuario_id", length = 36, nullable = false)
    private String usuarioId;

    @Column(name = "contacto_emergencia_nombre", length = 150)
    private String contactoEmergenciaNombre;

    @Column(name = "contacto_emergencia_parentesco", length = 50)
    private String contactoEmergenciaParentesco;

    @Column(name = "contacto_emergencia_telefono", length = 20)
    private String contactoEmergenciaTelefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por_id")
    private Usuario actualizadoPor;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
