package com.schoolplus.dto;

import com.schoolplus.model.Comentario;
import com.schoolplus.model.Publicacion;

import java.util.List;

/**
 * Una entrada del feed con todo lo que la vista necesita ya resuelto.
 *
 * <p>Existe para que la plantilla no dispare consultas: los recuentos de reacciones y de
 * votos, y si el usuario ya reaccionó o ya votó, se calculan en bloque para el feed entero
 * y llegan aquí. Con 20 publicaciones en pantalla, resolverlo desde la vista serían
 * decenas de consultas.</p>
 *
 * @param publicacion   la entrada.
 * @param comentarios   sus comentarios visibles, en orden.
 * @param reacciones    cuántas tiene.
 * @param yaReaccione   si el usuario actual ya marcó "me gusta".
 * @param opciones      resultados de la encuesta; vacío si no lo es.
 * @param miOpcionId    la opción que votó el usuario actual, o null.
 * @param puedoModerar  si el usuario actual puede ocultar esta entrada.
 */
public record EntradaMuro(Publicacion publicacion,
                          List<Comentario> comentarios,
                          long reacciones,
                          boolean yaReaccione,
                          List<OpcionEncuesta> opciones,
                          String miOpcionId,
                          boolean puedoModerar) {

    /**
     * Una opción de encuesta con su recuento y su porcentaje.
     *
     * @param porcentaje entero 0-100, ya calculado: dejar la división a la plantilla es
     *                   pedir una división por cero el día que nadie haya votado.
     */
    public record OpcionEncuesta(String id, String texto, long votos, int porcentaje) {

        /**
         * El porcentaje redondeado a múltiplos de 5, para elegir la clase de la barra.
         *
         * <p>Hace falta porque la barra no puede llevar {@code style="width:37%"} —la CSP
         * prohíbe estilos en línea— ni una clase Tailwind generada al vuelo como
         * {@code w-[37%]}: Tailwind compila mirando el código fuente y esa clase no
         * existiría en el CSS. La salida son 21 clases fijas, {@code barra-0} a
         * {@code barra-100}, y esto elige cuál.</p>
         */
        public int paso() {
            return Math.round(porcentaje / 5f) * 5;
        }
    }

    public long totalVotos() {
        return opciones.stream().mapToLong(OpcionEncuesta::votos).sum();
    }

    public boolean yaVote() {
        return miOpcionId != null;
    }
}
