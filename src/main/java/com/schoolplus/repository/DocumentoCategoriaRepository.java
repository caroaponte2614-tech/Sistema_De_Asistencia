package com.schoolplus.repository;

import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.DocumentoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoCategoriaRepository extends JpaRepository<DocumentoCategoria, String> {

    List<DocumentoCategoria> findByClaseAndActivoTrueOrderByNombreAsc(ClaseDocumento clase);
}
