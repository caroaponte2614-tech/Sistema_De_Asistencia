package com.schoolplus.repository;

import com.schoolplus.model.CertificadoLaboral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificadoLaboralRepository extends JpaRepository<CertificadoLaboral, String> {

    @Query("""
            select c from CertificadoLaboral c
            join fetch c.usuario
            where c.usuario.id = :usuarioId
            order by c.emitidoEn desc
            """)
    List<CertificadoLaboral> misCertificados(@Param("usuarioId") String usuarioId);

    Optional<CertificadoLaboral> findByCodigoVerificacion(String codigoVerificacion);
}
