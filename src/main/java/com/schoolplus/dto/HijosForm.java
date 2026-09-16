package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** El editor completo de hijos — el formulario de la ficha del colaborador. */
@Getter
@Setter
public class HijosForm {

    private List<HijoForm> hijos = new ArrayList<>();

    public List<HijoEntrada> aEntradas() {
        return hijos.stream()
                .filter(h -> h.getNombre() != null && !h.getNombre().isBlank())
                .map(HijoForm::aEntrada)
                .toList();
    }

    public static HijosForm desde(List<HijoEntrada> actuales) {
        HijosForm form = new HijosForm();
        for (HijoEntrada entrada : actuales) {
            HijoForm hijo = new HijoForm();
            hijo.setNombre(entrada.nombre());
            hijo.setFechaNacimiento(entrada.fechaNacimiento());
            form.getHijos().add(hijo);
        }
        return form;
    }
}
