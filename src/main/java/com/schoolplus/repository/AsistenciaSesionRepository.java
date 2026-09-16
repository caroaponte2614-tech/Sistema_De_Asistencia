package com.schoolplus.repository;

import com.schoolplus.model.AsistenciaSesion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AsistenciaSesionRepository extends JpaRepository<AsistenciaSesion, String> {

    /**
     * La sesión abierta de una clase, si la hay. Se busca por {@code claseAbiertaId} —la
     * columna del candado único— y no por estado, para que la pregunta y la restricción
     * que la hace cumplir sean exactamente la misma cosa.
     */
    Optional<AsistenciaSesion> findByClaseAbiertaId(String claseId);

    @Query("""
            select s from AsistenciaSesion s
            join fetch s.clase c
            join fetch c.profesor
            left join fetch s.horario
            where s.id = :id
            """)
    Optional<AsistenciaSesion> buscarCompleta(@Param("id") String id);

    /**
     * Las sesiones abiertas de las clases donde ese estudiante está matriculado.
     *
     * <p>Es lo que permite que el código de 6 dígitos no necesite decir de qué clase es:
     * se prueba contra las pocas sesiones en las que esa persona podría registrarse.</p>
     */
    @Query("""
            select s from AsistenciaSesion s
            join fetch s.clase c
            where s.estado = com.schoolplus.model.EstadoSesionAsistencia.ABIERTA
              and exists (select 1 from ClaseInscripcion i
                          where i.clase = c and i.estudiante.id = :estudianteId and i.activa = true)
            order by s.abiertaEn desc
            """)
    List<AsistenciaSesion> abiertasParaEstudiante(@Param("estudianteId") String estudianteId);

    @Query("""
            select s from AsistenciaSesion s
            join fetch s.clase c
            where c.id = :claseId
            order by s.abiertaEn desc
            """)
    List<AsistenciaSesion> ultimasDeClase(@Param("claseId") String claseId, Pageable pagina);

    /** Para el cierre automático: sesiones que quedaron abiertas más de la cuenta. */
    @Query("""
            select s from AsistenciaSesion s
            join fetch s.clase
            where s.estado = com.schoolplus.model.EstadoSesionAsistencia.ABIERTA
              and s.abiertaEn < :limite
            """)
    List<AsistenciaSesion> abiertasDesdeAntesDe(@Param("limite") LocalDateTime limite);

    /** Sesiones cerradas de un rango, para el reporte por sesión. */
    @Query("""
            select s from AsistenciaSesion s
            join fetch s.clase c
            join fetch c.profesor
            where s.estado = com.schoolplus.model.EstadoSesionAsistencia.CERRADA
              and s.abiertaEn >= :desde and s.abiertaEn < :hasta
              and (:claseId is null or c.id = :claseId)
              and (:profesorId is null or c.profesor.id = :profesorId)
            order by s.abiertaEn asc
            """)
    List<AsistenciaSesion> cerradasParaReporte(@Param("desde") LocalDateTime desde,
                                               @Param("hasta") LocalDateTime hasta,
                                               @Param("claseId") String claseId,
                                               @Param("profesorId") String profesorId);
}
