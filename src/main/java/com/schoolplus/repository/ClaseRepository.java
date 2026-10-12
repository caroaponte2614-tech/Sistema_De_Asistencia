package com.schoolplus.repository;

import com.schoolplus.model.Clase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaseRepository extends JpaRepository<Clase, String> {

    /** Las clases que dicta un docente. Es la lista de "mis clases" de la pantalla de asistencia. */
    @Query("""
            select c from Clase c
            join fetch c.profesor
            where c.profesor.id = :profesorId and c.activa = true
            order by c.periodo desc, c.nombre asc
            """)
    List<Clase> deProfesor(@Param("profesorId") String profesorId);

    /** Todas, para quien administra o tiene alcance TODOS. */
    @Query("""
            select c from Clase c
            join fetch c.profesor
            order by c.activa desc, c.periodo desc, c.nombre asc
            """)
    List<Clase> todasConProfesor();

    @Query("""
            select c from Clase c
            join fetch c.profesor
            where c.activa = true
            order by c.periodo desc, c.nombre asc
            """)
    List<Clase> activasConProfesor();

    @Query("""
            select c from Clase c
            join fetch c.profesor
            where c.id = :id
            """)
    Optional<Clase> buscarConProfesor(@Param("id") String id);
}
