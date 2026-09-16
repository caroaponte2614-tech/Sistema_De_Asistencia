package com.schoolplus.repository;

import com.schoolplus.model.PerfilHijo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerfilHijoRepository extends JpaRepository<PerfilHijo, String> {

    List<PerfilHijo> findByUsuarioIdOrderByOrdenAsc(String usuarioId);

    void deleteByUsuarioId(String usuarioId);
}
