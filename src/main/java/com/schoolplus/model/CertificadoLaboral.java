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

/**
 * Un certificado laboral ya emitido. El PDF se genera una sola vez y se guarda (no se
 * rearma en cada descarga), así el QR de verificación siempre apunta al mismo
 * documento. Sin {@code estado}: la emisión es inmediata, ver V21.
 */
@Entity
@Table(name = "certificado_laboral")
@Getter
@Setter
public class CertificadoLaboral {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por_id", nullable = false)
    private Usuario solicitadoPor;

    @Column(name = "codigo_verificacion", nullable = false, length = 20)
    private String codigoVerificacion;

    @Column(name = "pdf_clave", nullable = false, length = 500)
    private String pdfClave;

    @Column(name = "emitido_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime emitidoEn;
}
