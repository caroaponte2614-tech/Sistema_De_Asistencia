package com.schoolplus.repository;

import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.EstadoDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsistenciaDispositivoRepository extends JpaRepository<AsistenciaDispositivo, String> {

    /** El celular activo de un estudiante, si ya vinculó alguno. */
    Optional<AsistenciaDispositivo> findByActivoEstudianteId(String estudianteId);

    /** ¿Este celular ya está activo para alguien? */
    Optional<AsistenciaDispositivo> findByActivoIdentificador(String identificadorHash);

    Optional<AsistenciaDispositivo> findFirstByEstudianteIdAndIdentificadorHashAndEstado(
            String estudianteId, String identificadorHash, EstadoDispositivo estado);

    List<AsistenciaDispositivo> findByEstudianteIdAndEstado(String estudianteId, EstadoDispositivo estado);

    List<AsistenciaDispositivo> findByEstudianteIdOrderBySolicitadoEnDesc(String estudianteId);

    /** Solicitudes de cambio de celular de los estudiantes de un docente. */
    @Query("""
            select d from AsistenciaDispositivo d
            join fetch d.estudiante e
            where d.estado = com.schoolplus.model.EstadoDispositivo.PENDIENTE
              and (:profesorId is null
                   or exists (select 1 from ClaseInscripcion i
                              where i.estudiante = e and i.activa = true and i.clase.profesor.id = :profesorId))
            order by d.solicitadoEn asc
            """)
    List<AsistenciaDispositivo> pendientes(@Param("profesorId") String profesorId);

    @Query("""
            select d from AsistenciaDispositivo d
            join fetch d.estudiante
            where d.id = :id
            """)
    Optional<AsistenciaDispositivo> buscarConEstudiante(@Param("id") String id);
}
