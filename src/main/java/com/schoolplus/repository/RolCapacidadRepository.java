package com.schoolplus.repository;

import com.schoolplus.dto.CapacidadConcedida;
import com.schoolplus.model.RolCapacidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RolCapacidadRepository extends JpaRepository<RolCapacidad, RolCapacidad.Id> {

    /**
     * Capacidades efectivas de un conjunto de roles.
     *
     * <p>Filtra por {@code habilitado}, así que las filas marcadas con
     * {@code pendiente_confirmacion} —los ❔ de la matriz del prompt— quedan fuera hasta
     * que la administración las confirme.</p>
     *
     * <p>{@code distinct} porque una persona con dos roles puede recibir la misma
     * capacidad por ambos.</p>
     */
    @Query("""
            select distinct new com.schoolplus.dto.CapacidadConcedida(
                    c.codigo, c.descripcion, c.modulo, rc.ambito)
            from RolCapacidad rc
            join rc.capacidad c
            where rc.id.rolCodigo in :roles
              and rc.habilitado = true
            order by c.modulo, c.descripcion
            """)
    List<CapacidadConcedida> capacidadesDeRoles(@Param("roles") Collection<String> roles);
}
