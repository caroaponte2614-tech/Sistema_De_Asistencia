package com.schoolplus.repository;

import com.schoolplus.model.Curso;
import com.schoolplus.model.TipoCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CursoRepository extends JpaRepository<Curso, String> {

    /** El catálogo que alimenta el carrusel y la grilla de capacitaciones. */
    List<Curso> findByPublicadoTrueAndTipoOrderByOrdenAscCreadoEnDesc(TipoCurso tipo);

    /** El curso de inducción vigente, si hay uno publicado. */
    Optional<Curso> findFirstByPublicadoTrueAndTipoAndObligatorioTrueOrderByCreadoEnDesc(TipoCurso tipo);

    /** Todo, publicado o no — el listado de administración de Talento Humano. */
    List<Curso> findAllByOrderByCreadoEnDesc();

    @Query("""
            select c from Curso c
            join fetch c.creadoPor
            where c.id = :id
            """)
    Optional<Curso> buscarCompleto(@Param("id") String id);
}
