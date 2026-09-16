package com.schoolplus.repository;

import com.schoolplus.model.Tablero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TableroRepository extends JpaRepository<Tablero, String> {

    Optional<Tablero> findByUsuarioId(String usuarioId);

    @Query("""
            select distinct t from Tablero t
            join fetch t.usuario
            left join fetch t.columnas
            where t.usuario.id = :usuarioId
            """)
    Optional<Tablero> buscarConColumnas(@Param("usuarioId") String usuarioId);

    @Query("""
            select distinct t from Tablero t
            join fetch t.area
            left join fetch t.columnas
            where t.area.id = :areaId
            """)
    Optional<Tablero> buscarPorAreaConColumnas(@Param("areaId") String areaId);
}
