package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Fila mutable del editor de opciones.
 *
 * <p>Sin campo {@code correcta} a propósito: cuál es la correcta se elige con un radio
 * button por pregunta (un solo índice posible, ver {@link PreguntaForm#getCorrectaIndice()}),
 * no con un checkbox por opción — así la regla "exactamente una correcta" queda impuesta
 * por la propia forma del control, no por una validación aparte.</p>
 */
@Getter
@Setter
public class OpcionForm {

    private String texto;
}
