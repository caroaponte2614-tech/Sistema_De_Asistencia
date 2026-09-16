package com.schoolplus.repository;

import com.schoolplus.model.CursoPregunta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CursoPreguntaRepository extends JpaRepository<CursoPregunta, String> {

    List<CursoPregunta> findByCursoIdOrderByOrdenAsc(String cursoId);

    void deleteByCursoId(String cursoId);
}
