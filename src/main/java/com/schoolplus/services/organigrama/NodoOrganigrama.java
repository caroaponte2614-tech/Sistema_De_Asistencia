package com.schoolplus.services.organigrama;

import com.schoolplus.model.Usuario;

import java.util.List;

/** Una persona y su equipo directo, para pintar el árbol de "quién es jefe de quién". */
public record NodoOrganigrama(Usuario persona, List<NodoOrganigrama> subordinados) {
}
