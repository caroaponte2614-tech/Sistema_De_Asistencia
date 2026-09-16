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

    /** Candidatos a jefe o a colaborador en la pantalla de organigrama: solo quien puede entrar. */
    List<Usuario> findByEstadoOrderByNombreAsc(EstadoUsuario estado);

    /**
     * Igual que {@link #findByEstadoOrderByNombreAsc}, pero con cargo y jefe directo ya
     * resueltos: hace falta para calcular las excepciones manuales del organigrama sin
     * reventar con {@code LazyInitializationException} ({@code open-in-view=false}).
     */
    @Query("""
            select u from Usuario u
            left join fetch u.cargo
            left join fetch u.coordinador
            where u.estado = :estado
            order by u.nombre asc
            """)
    List<Usuario> buscarActivosConCargoYCoordinador(@Param("estado") EstadoUsuario estado);

    /** El listado del módulo de usuarios: todos, activos e inactivos, con cargo y jefe. */
    @Query("""
            select u from Usuario u
            left join fetch u.cargo
            left join fetch u.coordinador
            order by u.nombre asc
            """)
    List<Usuario> buscarTodosConCargoYCoordinador();

    /** Ficha completa de un usuario para el módulo de administración: nada queda lazy. */
    @Query("""
            select u from Usuario u
            left join fetch u.sede
            left join fetch u.cargo
            left join fetch u.area
            left join fetch u.coordinador
            where u.id = :id
            """)
    Optional<Usuario> buscarCompletoPorId(@Param("id") String id);

    /** El equipo directo de un coordinador (§4.6: quién puede diligenciarle horas extra). */
    List<Usuario> findByCoordinadorAndEstadoOrderByNombreAsc(Usuario coordinador, EstadoUsuario estado);

    /**
     * Igual que {@link #findByCoordinadorAndEstadoOrderByNombreAsc}, pero con el cargo ya
     * resuelto — la pantalla "Mi equipo" lo muestra por persona, y sin este join fetch
     * revienta con {@code LazyInitializationException} ({@code open-in-view=false}).
     */
    @Query("""
            select u from Usuario u
            left join fetch u.cargo
            where u.coordinador = :coordinador and u.estado = :estado
            order by u.nombre asc
            """)
    List<Usuario> buscarEquipoConCargo(@Param("coordinador") Usuario coordinador, @Param("estado") EstadoUsuario estado);

    /** A quién avisar cuando algo llega a Talento Humano: no hay un destinatario único, es todo el rol. */
    @Query("""
            select u from Usuario u
            join u.roles r
            where r.codigo = :rolCodigo and u.estado = :estado
            """)
    List<Usuario> buscarActivosPorRol(@Param("rolCodigo") String rolCodigo, @Param("estado") EstadoUsuario estado);

    Optional<Usuario> findByCedula(String cedula);

    boolean existsByEmail(String email);

    boolean existsByCedula(String cedula);

    long countByEstado(EstadoUsuario estado);

    boolean existsByRolesCodigo(String rolCodigo);

    /**
     * Trae el usuario con su sede y su cargo ya resueltos.
     *
     * <p>Hace falta porque {@code spring.jpa.open-in-view=false}: sin el {@code join fetch},
     * la plantilla reventaría con {@code LazyInitializationException} al pintar el panel.</p>
     */
    @Query("""
            select u from Usuario u
            left join fetch u.sede
            left join fetch u.cargo
            where u.email = :email
            """)
    Optional<Usuario> buscarConSedeYCargoPorEmail(@Param("email") String email);

    /**
     * El actor autenticado, con las cuatro relaciones {@code ManyToOne} ya resueltas
     * (sede, cargo, área, jefe directo). La usa {@code ActorArgumentResolver}: como
     * resuelve el {@code @Actor Usuario} ANTES de que arranque el método del controlador
     * —y por lo tanto en su propia transacción corta, ya cerrada para cuando el
     * controlador se ejecuta—, un {@code findByEmail} simple dejaría esas cuatro
     * relaciones como proxies muertos. Sin este join fetch, tocar
     * {@code actor.getCoordinador()} en el cuerpo de un método revienta con
     * {@code LazyInitializationException} aunque antes, con el viejo
     * {@code actor(Authentication)} llamado dentro del propio método transaccional,
     * nunca hubiera fallado.
     */
    @Query("""
            select u from Usuario u
            left join fetch u.sede
            left join fetch u.cargo
            left join fetch u.area
            left join fetch u.coordinador
            where u.email = :email
            """)
    Optional<Usuario> buscarActorPorEmail(@Param("email") String email);
}
