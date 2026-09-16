package com.schoolplus.repository;

import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.EstadoAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsistenciaRegistroRepository extends JpaRepository<AsistenciaRegistro, String> {

    /** Máximo un registro por estudiante y sesión: esta es la comprobación anticipada. */
    Optional<AsistenciaRegistro> findBySesionIdAndEstudianteId(String sesionId, String estudianteId);

    /** Un mismo celular no registra a dos personas en la misma sesión. */
    boolean existsBySesionIdAndDispositivoId(String sesionId, String dispositivoId);

    long countBySesionIdAndEstadoIn(String sesionId, Collection<EstadoAsistencia> estados);

    /** La lista en vivo de la pantalla del profesor, y la del detalle al cerrar. */
    @Query("""
            select r from AsistenciaRegistro r
            join fetch r.estudiante
            where r.sesion.id = :sesionId
            order by r.registradoEn asc, r.estudiante.nombre asc
            """)
    List<AsistenciaRegistro> deSesion(@Param("sesionId") String sesionId);

    @Query("""
            select r from AsistenciaRegistro r
            join fetch r.sesion s
            join fetch s.clase c
            join fetch r.estudiante
            where r.id = :id
            """)
    Optional<AsistenciaRegistro> buscarCompleto(@Param("id") String id);

    /** El historial que cada estudiante ve desde su celular. */
    @Query("""
            select r from AsistenciaRegistro r
            join fetch r.sesion s
            join fetch s.clase c
            where r.estudiante.id = :estudianteId
            order by s.abiertaEn desc
            """)
    List<AsistenciaRegistro> historialDe(@Param("estudianteId") String estudianteId);

    /**
     * El grano del reporte: un registro por estudiante y sesión.
     *
     * <p>Solo sesiones CERRADAS. Una sesión abierta todavía no tiene ausentes —los crea el
     * cierre—, así que contarla dejaría porcentajes que cambian solos mientras se mira la
     * pantalla.</p>
     */
    @Query("""
            select r from AsistenciaRegistro r
            join fetch r.sesion s
            join fetch s.clase c
            join fetch r.estudiante e
            where s.estado = com.schoolplus.model.EstadoSesionAsistencia.CERRADA
              and s.abiertaEn >= :desde and s.abiertaEn < :hasta
              and (:claseId is null or c.id = :claseId)
              and (:estudianteId is null or e.id = :estudianteId)
              and (:profesorId is null or c.profesor.id = :profesorId)
            order by s.abiertaEn asc, e.nombre asc
            """)
    List<AsistenciaRegistro> paraReporte(@Param("desde") LocalDateTime desde,
                                         @Param("hasta") LocalDateTime hasta,
                                         @Param("claseId") String claseId,
                                         @Param("estudianteId") String estudianteId,
                                         @Param("profesorId") String profesorId);
}
