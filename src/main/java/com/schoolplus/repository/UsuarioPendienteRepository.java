package com.schoolplus.repository;

import com.schoolplus.model.UsuarioPendiente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioPendienteRepository extends JpaRepository<UsuarioPendiente, String> {

    Optional<UsuarioPendiente> findByEmail(String email);

    Optional<UsuarioPendiente> findByCedula(String cedula);

    Optional<UsuarioPendiente> findByToken(String token);
}
