package com.schoolplus.repository;

import com.schoolplus.model.VacacionesSaldo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VacacionesSaldoRepository extends JpaRepository<VacacionesSaldo, String> {

    @Query("""
            select s from VacacionesSaldo s
            where s.usuario.id = :usuarioId
            order by s.periodo
            """)
    List<VacacionesSaldo> saldosDe(@Param("usuarioId") String usuarioId);

    Optional<VacacionesSaldo> findByUsuarioIdAndPeriodo(String usuarioId, short periodo);

    /**
     * Trae el saldo con BLOQUEO PESIMISTA de escritura ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Es el único sitio del sistema donde se usa bloqueo pesimista, y el §5.2 pide
     * documentar por qué. El motivo: descontar saldo es leer-decidir-escribir, y dos
     * solicitudes simultáneas que caben por separado pero no juntas tienen que
     * serializarse. Con bloqueo optimista la segunda reventaría con un 409 después de
     * haber comprobado un saldo que ya no era cierto; aquí espera su turno y ve el saldo
     * con la reserva de la primera ya aplicada.</p>
     *
     * <p>La ventana del bloqueo es corta a propósito: solo el tramo de comprobar y
     * reservar, nunca abarcando envíos de correo ni ninguna llamada de red (§5.2).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from VacacionesSaldo s
            where s.usuario.id = :usuarioId and s.periodo = :periodo
            """)
    Optional<VacacionesSaldo> bloquearParaActualizar(@Param("usuarioId") String usuarioId,
                                                     @Param("periodo") short periodo);
}
