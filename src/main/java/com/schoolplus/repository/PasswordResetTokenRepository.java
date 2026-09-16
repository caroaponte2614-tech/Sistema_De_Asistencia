package com.schoolplus.repository;

import com.schoolplus.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    @Query("""
            select t from PasswordResetToken t
            join fetch t.usuario
            where t.token = :token
            """)
    Optional<PasswordResetToken> buscarConUsuarioPorToken(@Param("token") String token);

    /**
     * Invalida los enlaces anteriores de esa persona.
     *
     * <p>Origen de la regla: un proyecto de referencia (services/password/PasswordResetService.java),
     * que hacía {@code deleteByUsuarioId} antes de crear el nuevo. Aquí se marcan como
     * usados en vez de borrarlos, para no perder el rastro de cuántos enlaces se pidieron.</p>
     */
    @Modifying
    @Query("""
            update PasswordResetToken t
            set t.usadoEn = CURRENT_TIMESTAMP
            where t.usuario.id = :usuarioId and t.usadoEn is null
            """)
    int invalidarVigentesDe(@Param("usuarioId") String usuarioId);
}
