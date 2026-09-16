package com.schoolplus.repository;

import com.schoolplus.model.Sede;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SedeRepository extends JpaRepository<Sede, String> {

    List<Sede> findByActivoTrueOrderByNombreAsc();

    long countByActivoTrue();
}
