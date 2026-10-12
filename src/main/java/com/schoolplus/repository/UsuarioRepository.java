package com.schoolplus.repository;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByEstadoOrderByNombreAsc(EstadoUsuario estado);

    /** El listado del módulo de usuarios: todos, activos e inactivos. */
    List<Usuario> findAllByOrderByNombreAsc();

    /** Los usuarios activos de un rol; por ejemplo, los estudiantes que se pueden matricular. */
    @Query("""
            select u from Usuario u
            join u.roles r
            where r.codigo = :rolCodigo and u.estado = :estado
            order by u.nombre asc
            """)
    List<Usuario> buscarActivosPorRol(@Param("rolCodigo") String rolCodigo, @Param("estado") EstadoUsuario estado);

    Optional<Usuario> findByCedula(String cedula);

    boolean existsByEmail(String email);

    boolean existsByCedula(String cedula);

    long countByEstado(EstadoUsuario estado);

    boolean existsByRolesCodigo(String rolCodigo);
}
