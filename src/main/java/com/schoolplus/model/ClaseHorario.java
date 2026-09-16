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
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Una franja semanal de una clase ("martes 10:00–11:00, aula 204").
 *
 * <p>Sirve para mostrar el horario y, sobre todo, para que la sesión de asistencia sepa a
 * qué hora <em>empezaba</em> la clase: la tardanza se mide desde ahí y no desde el momento
 * en que el profesor se acordó de abrir el QR.</p>
 */
@Entity
@Table(name = "clase_horario")
@Getter
@Setter
public class ClaseHorario {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private Clase clase;

    /** Se guarda el nombre del día en inglés porque es el de {@link DayOfWeek}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", length = 10, nullable = false)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "aula", length = 60)
    private String aula;
}
