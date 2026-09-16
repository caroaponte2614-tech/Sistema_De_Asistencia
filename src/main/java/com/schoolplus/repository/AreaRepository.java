package com.schoolplus.repository;

import com.schoolplus.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, String> {

    List<Area> findByActivoTrueOrderByNombreAsc();

    /** El listado del módulo "Áreas y líderes": activas e inactivas, para poder reactivarlas. */
    List<Area> findAllByOrderByNombreAsc();

    boolean existsByNombre(String nombre);
}
