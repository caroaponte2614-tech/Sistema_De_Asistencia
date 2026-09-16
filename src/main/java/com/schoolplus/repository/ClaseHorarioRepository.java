package com.schoolplus.repository;

import com.schoolplus.model.ClaseHorario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaseHorarioRepository extends JpaRepository<ClaseHorario, String> {

    /**
     * Las franjas de una clase.
     *
     * <p>El orden por día se hace en memoria y no en SQL a propósito: la columna guarda el
     * nombre del día ({@code MONDAY}, {@code TUESDAY}…) y ordenarla alfabéticamente pondría
     * el viernes antes que el lunes.</p>
     */
    List<ClaseHorario> findByClaseId(String claseId);

    long countByClaseId(String claseId);
}
