package com.schoolplus.repository;

import com.schoolplus.model.Reaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReaccionRepository extends JpaRepository<Reaccion, String> {

    Optional<Reaccion> findByPublicacionIdAndUsuarioId(String publicacionId, String usuarioId);

    Optional<Reaccion> findByComentarioIdAndUsuarioId(String comentarioId, String usuarioId);

    /** Cuántas reacciones tiene cada publicación del feed. Una sola consulta. */
    @Query("""
            select r.publicacion.id, count(r)
            from Reaccion r
            where r.publicacion.id in :publicacionIds
            group by r.publicacion.id
            """)
    List<Object[]> contarPorPublicacion(@Param("publicacionIds") Collection<String> publicacionIds);

    /** Cuáles de esas publicaciones ya marcó ESTE usuario. */
    @Query("""
            select r.publicacion.id
            from Reaccion r
            where r.usuario.id = :usuarioId and r.publicacion.id in :publicacionIds
            """)
    List<String> misReacciones(@Param("usuarioId") String usuarioId,
                               @Param("publicacionIds") Collection<String> publicacionIds);
}
