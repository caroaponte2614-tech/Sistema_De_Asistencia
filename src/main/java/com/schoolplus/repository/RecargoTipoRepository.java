package com.schoolplus.repository;

import com.schoolplus.model.RecargoTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecargoTipoRepository extends JpaRepository<RecargoTipo, String> {

    List<RecargoTipo> findByActivoTrueOrderByNombreAsc();
}
