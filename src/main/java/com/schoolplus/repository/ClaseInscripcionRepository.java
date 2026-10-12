package com.schoolplus.repository;

import com.schoolplus.model.ClaseInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaseInscripcionRepository extends JpaRepository<ClaseInscripcion, String> {

    /** La pregunta del antifraude: ¿este estudiante pertenece a esta clase? */
    boolean existsByClaseIdAndEstudianteIdAndActivaTrue(String claseId, String estudianteId);

    Optional<ClaseInscripcion> findByClaseIdAndEstudianteId(String claseId, String estudianteId);

    /** La lista de clase, para el cierre (quién faltó) y para marcar a mano. */
    @Query("""
            select i from ClaseInscripcion i
            join fetch i.estudiante
            where i.clase.id = :claseId and i.activa = true
            order by i.estudiante.nombre asc
            """)
    List<ClaseInscripcion> inscritosDe(@Param("claseId") String claseId);

    long countByClaseIdAndActivaTrue(String claseId);

    /** Las clases de un estudiante: su pantalla de asistencia y su historial. */
    @Query("""
            select i from ClaseInscripcion i
            join fetch i.clase c
            join fetch c.profesor
            where i.estudiante.id = :estudianteId and i.activa = true and c.activa = true
            order by c.nombre asc
            """)
    List<ClaseInscripcion> clasesDe(@Param("estudianteId") String estudianteId);

    /**
     * ¿Ese estudiante está en alguna clase de ese docente? Es lo que autoriza a un profesor
     * a aprobarle un cambio de celular: sobre sus estudiantes sí, sobre los demás no.
     */
    boolean existsByEstudianteIdAndClaseProfesorIdAndActivaTrue(String estudianteId, String profesorId);

    /** Los profesores de un estudiante, sin repetir: a quiénes avisar de un cambio de celular. */
    @Query("""
            select distinct c.profesor.id from ClaseInscripcion i
            join i.clase c
            where i.estudiante.id = :estudianteId and i.activa = true and c.activa = true
            """)
    List<String> profesoresDe(@Param("estudianteId") String estudianteId);
}
