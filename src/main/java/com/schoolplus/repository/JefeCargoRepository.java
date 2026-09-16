package com.schoolplus.repository;

import com.schoolplus.model.JefeCargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JefeCargoRepository extends JpaRepository<JefeCargo, String> {

    List<JefeCargo> findByJefeIdOrderByCargo_NombreAsc(String jefeId);

    void deleteByJefeIdAndCargoIdNotIn(String jefeId, List<String> cargoIds);

    /**
     * El organigrama completo, para pintarlo de una vez. Con {@code join fetch}: sin él,
     * la plantilla reventaría con {@code LazyInitializationException} al leer
     * {@code cargo.nombre} / {@code jefe.nombre} ({@code open-in-view=false}, ver
     * {@code UsuarioRepository.buscarConSedeYCargoPorEmail}).
     */
    @Query("""
            select jc from JefeCargo jc
            join fetch jc.cargo
            join fetch jc.jefe
            order by jc.cargo.nombre asc
            """)
    List<JefeCargo> findAllConCargoYJefeOrderByCargo_NombreAsc();
}
