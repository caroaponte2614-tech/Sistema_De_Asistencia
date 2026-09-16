package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Fila mutable del editor de hijos, para el binding indexado del formulario. */
@Getter
@Setter
public class HijoForm {

    private String nombre;
    private LocalDate fechaNacimiento;

    public HijoEntrada aEntrada() {
        return new HijoEntrada(nombre, fechaNacimiento);
    }
}
