package com.schoolplus.repository;

import com.schoolplus.model.AuditoriaAcceso;
import com.schoolplus.model.EventoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditoriaAccesoRepository extends JpaRepository<AuditoriaAcceso, Long> {

    long countByEmailIntentadoAndEventoAndOcurridoEnAfter(
            String emailIntentado, EventoAcceso evento, LocalDateTime desde);

    long countByIpAndEventoAndOcurridoEnAfter(
            String ip, EventoAcceso evento, LocalDateTime desde);
}
