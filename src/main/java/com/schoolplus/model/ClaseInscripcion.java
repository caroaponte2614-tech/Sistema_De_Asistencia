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
 * La matrícula de un estudiante en una clase. Es la que decide quién puede registrar
 * asistencia en una sesión: nadie que no esté aquí, aunque tenga un QR válido.
 *
 * <p>Retirar a alguien es {@code activa = false}, nunca borrar la fila: los registros de
 * asistencia de los meses anteriores tienen que seguir significando algo.</p>
 */
@Entity
@Table(name = "clase_inscripcion")
@Getter
@Setter
public class ClaseInscripcion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private Clase clase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Usuario estudiante;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "inscrito_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime inscritoEn;
}
