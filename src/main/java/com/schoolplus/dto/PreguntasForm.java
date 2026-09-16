package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** El editor completo del quiz de un curso — el formulario de {@code preguntas.html}. */
@Getter
@Setter
public class PreguntasForm {

    private List<PreguntaForm> preguntas = new ArrayList<>();

    public List<PreguntaEntrada> aEntradas() {
        return preguntas.stream().map(PreguntaForm::aEntrada).toList();
    }

    public static PreguntasForm desde(List<PreguntaEntrada> actuales) {
        PreguntasForm form = new PreguntasForm();
        for (PreguntaEntrada entrada : actuales) {
            PreguntaForm pregunta = new PreguntaForm();
            pregunta.setEnunciado(entrada.enunciado());
            pregunta.setTipo(entrada.tipo());
            List<OpcionEntrada> opciones = entrada.opciones();
            for (int i = 0; i < opciones.size(); i++) {
                OpcionForm opcion = new OpcionForm();
                opcion.setTexto(opciones.get(i).texto());
                pregunta.getOpciones().add(opcion);
                if (opciones.get(i).correcta()) {
                    pregunta.setCorrectaIndice(i);
                }
            }
            form.getPreguntas().add(pregunta);
        }
        return form;
    }
}
