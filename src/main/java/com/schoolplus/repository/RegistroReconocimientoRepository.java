package com.schoolplus.repository;

import com.schoolplus.model.RegistroReconocimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegistroReconocimientoRepository extends JpaRepository<RegistroReconocimiento, String> {

    @Query("""
            select r from RegistroReconocimiento r
            join fetch r.colaborador
            join fetch r.registradoPor
            where r.id = :id
            """)
    Optional<RegistroReconocimiento> buscarCompleto(@Param("id") String id);

    @Query("""
            select r from RegistroReconocimiento r
            join fetch r.registradoPor
            where r.colaborador.id = :colaboradorId
            order by r.fecha desc
            """)
    List<RegistroReconocimiento> historialDe(@Param("colaboradorId") String colaboradorId);
}
