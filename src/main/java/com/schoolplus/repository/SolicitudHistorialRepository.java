package com.schoolplus.repository;

import com.schoolplus.model.SolicitudHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SolicitudHistorialRepository extends JpaRepository<SolicitudHistorial, Long> {

    @Query("""
            select h from SolicitudHistorial h
            left join fetch h.actor
            where h.solicitud.id = :solicitudId
            order by h.ocurridoEn, h.id
            """)
    List<SolicitudHistorial> historialDe(@Param("solicitudId") String solicitudId);
}
