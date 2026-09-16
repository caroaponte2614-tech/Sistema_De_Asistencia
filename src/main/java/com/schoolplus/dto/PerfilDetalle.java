package com.schoolplus.dto;

import com.schoolplus.model.PerfilColaborador;
import com.schoolplus.model.PerfilEvaluacion;
import com.schoolplus.model.PerfilHijo;
import com.schoolplus.model.Usuario;

import java.util.List;

/**
 * La ficha completa de un colaborador para la pantalla de identificación.
 *
 * @param perfil       datos básicos, {@code null} si todavía nadie los llenó.
 * @param hijos        estado actual, ya ordenados.
 * @param evaluaciones histórico, más reciente primero.
 */
public record PerfilDetalle(Usuario colaborador, PerfilColaborador perfil,
                            List<PerfilHijo> hijos, List<PerfilEvaluacion> evaluaciones) {
}
