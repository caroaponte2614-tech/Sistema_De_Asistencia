package com.schoolplus.repository;

import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, String> {

    @Query("""
            select d from Documento d
            join fetch d.categoria c
            where d.usuario.id = :usuarioId
              and c.clase = :clase
              and d.archivado = false
            order by c.nombre, d.titulo
            """)
    List<Documento> buscarDeLaCarpeta(@Param("usuarioId") String usuarioId, @Param("clase") ClaseDocumento clase);

    @Query("""
            select d from Documento d
            join fetch d.categoria
            join fetch d.usuario
            where d.id = :id
            """)
    Optional<Documento> buscarCompleto(@Param("id") String id);
}
