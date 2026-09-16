package com.schoolplus.repository;

import com.schoolplus.model.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComentarioRepository extends JpaRepository<Comentario, String> {

    @Query("""
            select c from Comentario c
            join fetch c.autor
            where c.id = :id
            """)
    Optional<Comentario> buscarConAutor(@Param("id") String id);

    /**
     * Comentarios de varias publicaciones de una vez.
     *
     * <p>Se cargan en bloque para el feed entero y no publicación por publicación: con 20
     * entradas en pantalla, lo segundo son 20 consultas — el problema N+1 de siempre.</p>
     */
    @Query("""
            select c from Comentario c
            join fetch c.autor
            where c.publicacion.id in :publicacionIds
            order by c.creadoEn asc
            """)
    List<Comentario> deLasPublicaciones(@Param("publicacionIds") Collection<String> publicacionIds);
}
