package com.schoolplus.dto;

import com.schoolplus.model.Curso;
import com.schoolplus.model.CursoInscripcion;

import java.util.List;

/**
 * Lo que necesita la pantalla de un curso: el curso, su quiz sin revelar las respuestas
 * correctas, y el progreso del colaborador que lo está mirando (null si nunca lo abrió).
 */
public record CursoDetalle(Curso curso, List<PreguntaVista> preguntas, CursoInscripcion inscripcion) {
}
