package com.schoolplus.repository;

import com.schoolplus.model.CursoOpcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CursoOpcionRepository extends JpaRepository<CursoOpcion, String> {

    List<CursoOpcion> findByPreguntaIdOrderByOrdenAsc(String preguntaId);
}
