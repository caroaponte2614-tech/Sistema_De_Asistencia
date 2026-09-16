package com.schoolplus.repository;

import com.schoolplus.model.EncuestaVoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EncuestaVotoRepository extends JpaRepository<EncuestaVoto, String> {

    Optional<EncuestaVoto> findByPublicacionIdAndUsuarioId(String publicacionId, String usuarioId);

    /** Recuento por opción de las encuestas visibles en el feed. */
    @Query("""
            select v.opcion.id, count(v)
            from EncuestaVoto v
            where v.publicacion.id in :publicacionIds
            group by v.opcion.id
            """)
    List<Object[]> contarPorOpcion(@Param("publicacionIds") Collection<String> publicacionIds);

    /** Qué opción eligió ESTE usuario en cada encuesta. */
    @Query("""
            select v.publicacion.id, v.opcion.id
            from EncuestaVoto v
            where v.usuario.id = :usuarioId and v.publicacion.id in :publicacionIds
            """)
    List<Object[]> misVotos(@Param("usuarioId") String usuarioId,
                            @Param("publicacionIds") Collection<String> publicacionIds);
}
