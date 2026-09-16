package com.schoolplus.repository;

import com.schoolplus.model.DocumentoVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, String> {

    List<DocumentoVersion> findByDocumentoIdOrderByNumeroDesc(String documentoId);

    Optional<DocumentoVersion> findByDocumentoIdAndNumero(String documentoId, int numero);
}
