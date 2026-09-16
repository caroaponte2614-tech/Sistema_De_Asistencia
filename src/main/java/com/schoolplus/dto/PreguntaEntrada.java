package com.schoolplus.dto;

import com.schoolplus.model.TipoPregunta;

import java.util.List;

/** Una pregunta tal como la escribe quien arma el quiz de un curso. */
public record PreguntaEntrada(String enunciado, TipoPregunta tipo, List<OpcionEntrada> opciones) {
}
