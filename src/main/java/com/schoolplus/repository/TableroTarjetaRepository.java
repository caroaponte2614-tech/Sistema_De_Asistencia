package com.schoolplus.repository;

import com.schoolplus.model.TableroTarjeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableroTarjetaRepository extends JpaRepository<TableroTarjeta, String> {

    @Query("""
            select t from TableroTarjeta t
            join fetch t.columna c
            join fetch t.creadoPor
            where c.tablero.id = :tableroId
            order by c.orden asc, t.orden asc
            """)
    List<TableroTarjeta> delTablero(@Param("tableroId") String tableroId);

    @Query("""
            select t from TableroTarjeta t
            join fetch t.columna c
            join fetch c.tablero tb
            left join fetch tb.usuario
            left join fetch tb.area
            where t.id = :id
            """)
    Optional<TableroTarjeta> buscarConTablero(@Param("id") String id);

    /** Siguiente hueco al final de una columna. */
    @Query("select coalesce(max(t.orden), -1) + 1 from TableroTarjeta t where t.columna.id = :columnaId")
    int siguienteOrden(@Param("columnaId") String columnaId);

    /** Vencidas, sin terminar ("Hecho" no cuenta) y que todavía no avisaron. Para el recordatorio diario. */
    @Query("""
            select t from TableroTarjeta t
            join fetch t.columna c
            join fetch c.tablero tb
            join fetch tb.usuario
            where t.fechaLimite is not null
              and t.fechaLimite <= :ahora
              and t.recordatorioEnviado = false
              and c.codigo <> 'HECHO'
            """)
    List<TableroTarjeta> pendientesDeRecordatorio(@Param("ahora") LocalDateTime ahora);
}
