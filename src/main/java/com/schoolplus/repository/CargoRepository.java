package com.schoolplus.repository;

import com.schoolplus.model.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CargoRepository extends JpaRepository<Cargo, String> {

    List<Cargo> findByActivoTrueOrderByNombreAsc();
}
