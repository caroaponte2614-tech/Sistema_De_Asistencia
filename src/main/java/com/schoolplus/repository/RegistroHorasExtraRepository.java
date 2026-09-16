package com.schoolplus.repository;

import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.RegistroHorasExtra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface RegistroHorasExtraRepository extends JpaRepository<RegistroHorasExtra, String> {

    @Query("""
            select r from RegistroHorasExtra r
            join fetch r.colaborador
            join fetch r.recargo
            join fetch r.registradoPor
            left join fetch r.decididoPor
            where r.id = :id
            """)
    Optional<RegistroHorasExtra> buscarCompleto(@Param("id") String id);

    /** Lo que ve el propio colaborador sobre sí mismo. Paginado obligatorio (§5.3). */
    @Query(value = """
            select r from RegistroHorasExtra r
            join fetch r.recargo
            join fetch r.registradoPor
            where r.colaborador.id = :colaboradorId
            order by r.fecha desc
            """,
            countQuery = "select count(r) from RegistroHorasExtra r where r.colaborador.id = :colaboradorId")
    Page<RegistroHorasExtra> misRegistros(@Param("colaboradorId") String colaboradorId, Pageable pagina);

    /** Lo que un coordinador registró para su equipo. */
    @Query(value = """
            select r from RegistroHorasExtra r
            join fetch r.colaborador
            join fetch r.recargo
            where r.registradoPor.id = :coordinadorId
            order by r.fecha desc
            """,
            countQuery = "select count(r) from RegistroHorasExtra r where r.registradoPor.id = :coordinadorId")
    Page<RegistroHorasExtra> registradosPor(@Param("coordinadorId") String coordinadorId, Pageable pagina);

    /** Bandeja del supervisor: todo lo pendiente, de cualquier coordinador. */
    @Query(value = """
            select r from RegistroHorasExtra r
            join fetch r.colaborador
            join fetch r.recargo
            join fetch r.registradoPor
            where r.estado = com.schoolplus.model.EstadoHorasExtra.PENDIENTE
            order by r.creadoEn
            """,
            countQuery = """
                    select count(r) from RegistroHorasExtra r
                    where r.estado = com.schoolplus.model.EstadoHorasExtra.PENDIENTE
                    """)
    Page<RegistroHorasExtra> pendientes(Pageable pagina);

    /** Historial completo para Talento Humano: todo, de cualquier coordinador, decidido o no. */
    @Query(value = """
            select r from RegistroHorasExtra r
            join fetch r.colaborador
            join fetch r.recargo
            join fetch r.registradoPor
            left join fetch r.decididoPor
            order by r.creadoEn desc
            """,
            countQuery = "select count(r) from RegistroHorasExtra r")
    Page<RegistroHorasExtra> todos(Pageable pagina);

    /** Evita el doble registro del mismo día y tipo mientras siga vivo (no aplica a lo ya rechazado). */
    boolean existsByColaboradorIdAndFechaAndRecargoCodigoAndEstadoIn(
            String colaboradorId, LocalDate fecha, String recargoCodigo,
            Collection<EstadoHorasExtra> estados);

    /** Para la píldora de pendientes del menú de Talento Humano. */
    long countByEstado(EstadoHorasExtra estado);
}
