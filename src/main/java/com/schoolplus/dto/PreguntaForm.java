package com.schoolplus.dto;

import com.schoolplus.model.TipoPregunta;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Fila mutable del editor de preguntas — {@link PreguntaEntrada} es su contraparte de solo lectura. */
@Getter
@Setter
public class PreguntaForm {

    private String enunciado;
    private TipoPregunta tipo = TipoPregunta.OPCION_UNICA;
    private List<OpcionForm> opciones = new ArrayList<>();

    /** Índice (0-based) de la opción marcada como correcta. Null si no se eligió ninguna. */
    private Integer correctaIndice;

    public PreguntaEntrada aEntrada() {
        List<OpcionEntrada> opcionesEntrada = new ArrayList<>();
        for (int i = 0; i < opciones.size(); i++) {
            opcionesEntrada.add(new OpcionEntrada(opciones.get(i).getTexto(),
                    correctaIndice != null && correctaIndice == i));
        }
        return new PreguntaEntrada(enunciado, tipo, opcionesEntrada);
    }
}
