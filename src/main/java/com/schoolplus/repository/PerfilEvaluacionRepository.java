package com.schoolplus.repository;

import com.schoolplus.model.PerfilEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerfilEvaluacionRepository extends JpaRepository<PerfilEvaluacion, String> {

    @Query("""
            select e from PerfilEvaluacion e
            join fetch e.evaluadoPor
            where e.usuario.id = :usuarioId
            order by e.fecha desc
            """)
    List<PerfilEvaluacion> findByUsuarioIdOrderByFechaDesc(@Param("usuarioId") String usuarioId);
}
