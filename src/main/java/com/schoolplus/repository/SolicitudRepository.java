package com.schoolplus.repository;

import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.model.Solicitud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitudRepository extends JpaRepository<Solicitud, String> {

    @Query("""
            select s from Solicitud s
            join fetch s.tipo
            left join fetch s.solicitante
            where s.id = :id
            """)
    Optional<Solicitud> buscarCompleta(@Param("id") String id);

    Optional<Solicitud> findByIdempotencyKey(String idempotencyKey);

    /** Mis solicitudes. Paginado obligatorio en todos los listados (§5.3). */
    @Query(value = """
            select s from Solicitud s
            join fetch s.tipo
            where s.solicitante.id = :usuarioId
            order by s.fechaInicio desc
            """,
            countQuery = "select count(s) from Solicitud s where s.solicitante.id = :usuarioId")
    Page<Solicitud> misSolicitudes(@Param("usuarioId") String usuarioId, Pageable pagina);

    /** Bandeja del coordinador: solo lo suyo, solo lo pendiente de él. */
    @Query(value = """
            select s from Solicitud s
            join fetch s.tipo
            join fetch s.solicitante
            where s.estado = com.schoolplus.model.EstadoSolicitud.PENDIENTE_COORDINADOR
              and s.coordinadorAsignado.id = :coordinadorId
            order by s.creadoEn
            """,
            countQuery = """
                    select count(s) from Solicitud s
                    where s.estado = com.schoolplus.model.EstadoSolicitud.PENDIENTE_COORDINADOR
                      and s.coordinadorAsignado.id = :coordinadorId
                    """)
    Page<Solicitud> bandejaCoordinador(@Param("coordinadorId") String coordinadorId, Pageable pagina);

    /** Bandeja de Talento Humano: todo lo que superó la primera instancia. */
    @Query(value = """
            select s from Solicitud s
            join fetch s.tipo
            join fetch s.solicitante
            where s.estado = com.schoolplus.model.EstadoSolicitud.PENDIENTE_SUPERVISOR
            order by s.creadoEn
            """,
            countQuery = """
                    select count(s) from Solicitud s
                    where s.estado = com.schoolplus.model.EstadoSolicitud.PENDIENTE_SUPERVISOR
                    """)
    Page<Solicitud> bandejaSupervisor(Pageable pagina);

    long countByEstadoAndCoordinadorAsignadoId(EstadoSolicitud estado, String coordinadorId);

    long countByEstado(EstadoSolicitud estado);

    /**
     * Cruces con el resto del equipo (§4.5). Es una ADVERTENCIA, no un bloqueo: el
     * método solo informa, quien decide es el aprobador.
     *
     * <p>Compañeros = quienes comparten coordinador con el solicitante. Dos rangos se
     * solapan si cada uno empieza antes de que el otro termine.</p>
     */
    @Query("""
            select s from Solicitud s
            join fetch s.solicitante u
            join fetch s.tipo
            where u.coordinador.id = :coordinadorId
              and u.id <> :excluyendoUsuarioId
              and s.estado in :estados
              and s.fechaInicio <= :fin
              and s.fechaFin >= :inicio
            order by s.fechaInicio
            """)
    List<Solicitud> crucesDelEquipo(@Param("coordinadorId") String coordinadorId,
                                    @Param("excluyendoUsuarioId") String excluyendoUsuarioId,
                                    @Param("inicio") LocalDate inicio,
                                    @Param("fin") LocalDate fin,
                                    @Param("estados") Collection<EstadoSolicitud> estados);
}
