package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una solicitud de permiso o de vacaciones. Una sola tabla para las dos familias (§4.5).
 *
 * <p>La entidad no cambia de estado por su cuenta: eso lo hace
 * {@code services.solicitudes.SolicitudWorkflowService}, que es el único sitio donde se
 * valida una transición y se escribe el historial. Aquí solo vive el dato.</p>
 */
@Entity
@Table(name = "solicitud")
@Getter
@Setter
public class Solicitud {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_codigo", nullable = false)
    private SolicitudTipo tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 30, nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.BORRADOR;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "dias_habiles", nullable = false, precision = 5, scale = 2)
    private BigDecimal diasHabiles;

    @Column(name = "motivo", length = 500)
    private String motivo;

    /** Soporte opcional (p.ej. una excusa médica) — un solo archivo, no un portafolio. */
    @Column(name = "archivo_clave", length = 500)
    private String archivoClave;

    @Column(name = "archivo_nombre_original", length = 255)
    private String archivoNombreOriginal;

    @Column(name = "archivo_mime", length = 100)
    private String archivoMime;

    @Column(name = "archivo_tamano_bytes")
    private Long archivoTamanoBytes;

    /**
     * Quién debe aprobar en primera instancia. Se fija AL CREAR y no se recalcula: si la
     * persona cambia de jefe a mitad de trámite, la solicitud sigue en la bandeja de
     * quien la tenía. NULL cuando entra directa a Talento Humano.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinador_asignado_id")
    private Usuario coordinadorAsignado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_coord_por_id")
    private Usuario decididoCoordPor;

    @Column(name = "decidido_coord_en")
    private LocalDateTime decididoCoordEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_rrhh_por_id")
    private Usuario decididoRrhhPor;

    @Column(name = "decidido_rrhh_en")
    private LocalDateTime decididoRrhhEn;

    /** Idempotencia de las aprobaciones (§5.2): el doble clic trae la misma clave. */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    /** Bloqueo optimista (§5.2). Dos aprobadores a la vez dan 409, no un pisotón. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean esDeVacaciones() {
        return tipo != null && tipo.isDescuentaSaldo();
    }
}
