package com.schoolplus.repository;

import com.schoolplus.model.SolicitudTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudTipoRepository extends JpaRepository<SolicitudTipo, String> {

    List<SolicitudTipo> findByActivoTrueOrderByNombreAsc();
}
