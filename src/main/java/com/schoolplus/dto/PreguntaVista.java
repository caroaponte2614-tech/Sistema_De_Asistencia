package com.schoolplus.dto;

import com.schoolplus.model.TipoPregunta;

import java.util.List;

/** Una pregunta del quiz tal como la ve el colaborador que la va a responder. */
public record PreguntaVista(String id, String enunciado, TipoPregunta tipo, List<OpcionVista> opciones) {
}
