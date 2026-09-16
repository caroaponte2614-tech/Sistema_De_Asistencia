package com.schoolplus.repository;

import com.schoolplus.model.CursoInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CursoInscripcionRepository extends JpaRepository<CursoInscripcion, String> {

    Optional<CursoInscripcion> findByCursoIdAndUsuarioId(String cursoId, String usuarioId);

    @Query("""
            select i from CursoInscripcion i
            join fetch i.curso
            where i.usuario.id = :usuarioId
            order by i.completadoEn desc nulls first
            """)
    List<CursoInscripcion> misInscripciones(@Param("usuarioId") String usuarioId);

    /** Para el reporte consolidado de Talento Humano. */
    @Query("""
            select i from CursoInscripcion i
            join fetch i.curso
            join fetch i.usuario
            order by i.curso.titulo, i.usuario.nombre
            """)
    List<CursoInscripcion> reporteFinalizacion();
}
