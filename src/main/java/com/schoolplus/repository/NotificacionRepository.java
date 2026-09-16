package com.schoolplus.repository;

import com.schoolplus.model.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, String> {

    Page<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(String usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(String usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.usuarioId = :usuarioId")
    int marcarLeida(@Param("id") String id, @Param("usuarioId") String usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.usuarioId = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") String usuarioId);
}
