package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/**
 * La matriz de permisos del §3.1, en datos.
 *
 * <p>Requisito literal del prompt: "implementar como tabla en BD, no como {@code if} en el
 * código". Cada fila concede una capacidad a un rol con un {@link Ambito}.</p>
 */
@Entity
@Table(name = "rol_capacidad")
@Getter
@Setter
public class RolCapacidad {

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("capacidadCodigo")
    @JoinColumn(name = "capacidad_codigo")
    private Capacidad capacidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", length = 10, nullable = false)
    private Ambito ambito;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    /**
     * Los puntos marcados con ❔ en la matriz del prompt. Quedan como fila apagada, no
     * como ausencia: así se sabe que la pregunta existe y está sin responder.
     */
    @Column(name = "pendiente_confirmacion", nullable = false)
    private boolean pendienteConfirmacion;

    @Embeddable
    @Getter
    @Setter
    public static class Id implements Serializable {

        @Column(name = "rol_codigo", length = 20, nullable = false)
        private String rolCodigo;

        @Column(name = "capacidad_codigo", length = 60, nullable = false)
        private String capacidadCodigo;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return Objects.equals(rolCodigo, id.rolCodigo)
                    && Objects.equals(capacidadCodigo, id.capacidadCodigo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rolCodigo, capacidadCodigo);
        }
    }
}
