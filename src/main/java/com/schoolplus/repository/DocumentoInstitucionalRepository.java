package com.schoolplus.repository;

import com.schoolplus.model.DocumentoInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoInstitucionalRepository extends JpaRepository<DocumentoInstitucional, String> {

    /** Lo que alimenta el carrusel del portal y el listado completo. */
    List<DocumentoInstitucional> findByPublicadoTrueAndArchivadoFalseOrderByOrdenAscCreadoEnDesc();

    /** El listado de administración: todo, publicado o no, salvo lo archivado. */
    List<DocumentoInstitucional> findByArchivadoFalseOrderByCreadoEnDesc();
}
