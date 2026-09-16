package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Que alguien abrió un {@link DocumentoInstitucional} — de ahí sale el chulo y el consolidado. */
@Entity
@Table(name = "documento_institucional_vista")
@Getter
@Setter
public class DocumentoInstitucionalVista {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoInstitucional documento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "primera_vez_en", nullable = false)
    private LocalDateTime primeraVezEn;

    @Column(name = "ultima_vez_en", nullable = false)
    private LocalDateTime ultimaVezEn;

    @Column(name = "veces_visto", nullable = false)
    private int vecesVisto = 1;
}
