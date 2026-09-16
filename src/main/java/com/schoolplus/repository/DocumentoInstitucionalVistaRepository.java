package com.schoolplus.repository;

import com.schoolplus.model.DocumentoInstitucionalVista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DocumentoInstitucionalVistaRepository extends JpaRepository<DocumentoInstitucionalVista, String> {

    Optional<DocumentoInstitucionalVista> findByDocumentoIdAndUsuarioId(String documentoId, String usuarioId);

    /** Para pintar el chulo en el listado sin una consulta por fila. */
    @Query("select v.documento.id from DocumentoInstitucionalVista v where v.usuario.id = :usuarioId")
    Set<String> documentosVistosPor(@Param("usuarioId") String usuarioId);

    /** El consolidado completo para el reporte de Talento Humano. */
    @Query("""
            select v from DocumentoInstitucionalVista v
            join fetch v.documento
            join fetch v.usuario
            order by v.documento.titulo, v.usuario.nombre
            """)
    List<DocumentoInstitucionalVista> reporteCompleto();
}
