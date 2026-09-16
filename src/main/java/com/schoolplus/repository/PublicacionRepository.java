package com.schoolplus.repository;

import com.schoolplus.model.EstadoPublicacion;
import com.schoolplus.model.Publicacion;
import com.schoolplus.model.TipoPublicacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PublicacionRepository extends JpaRepository<Publicacion, String> {

    @Query("""
            select p from Publicacion p
            join fetch p.autor
            left join fetch p.destinatario
            where p.id = :id
            """)
    Optional<Publicacion> buscarConAutor(@Param("id") String id);

    /**
     * El feed: fijadas arriba, luego lo más reciente. Paginado obligatorio (§5.3).
     *
     * <p>Un moderador ve también lo oculto —necesita poder revertir—; el resto no. El
     * filtro va en la consulta y no en la vista: ocultar en pantalla lo que el servidor ya
     * envió no es moderar, es disimular.</p>
     */
    @Query(value = """
            select p from Publicacion p
            join fetch p.autor
            left join fetch p.destinatario
            where (:incluirOcultas = true or p.estado = com.schoolplus.model.EstadoPublicacion.VISIBLE)
              and (:tipo is null or p.tipo = :tipo)
            order by p.fijada desc, p.publicadoEn desc
            """,
            countQuery = """
                    select count(p) from Publicacion p
                    where (:incluirOcultas = true or p.estado = com.schoolplus.model.EstadoPublicacion.VISIBLE)
                      and (:tipo is null or p.tipo = :tipo)
                    """)
    Page<Publicacion> feed(@Param("incluirOcultas") boolean incluirOcultas,
                           @Param("tipo") TipoPublicacion tipo,
                           Pageable pagina);

    long countByEstado(EstadoPublicacion estado);
}
