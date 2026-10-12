package com.schoolplus.util;

/**
 * Utilidades de texto chicas, reusadas en varios servicios y controladores.
 */
public final class Textos {

    private Textos() {
    }

    /**
     * {@code null} o solo espacios se vuelve {@code null}; cualquier otra cosa, recortada.
     *
     * <p>Para un {@code <select>} (horarioId...) el recorte no cambia nada — esos
     * valores nunca traen espacios de sobra. Para texto libre (título, nota, cuerpo) sí
     * importa: sin esto, un campo con solo espacios se guardaría como si tuviera
     * contenido en vez de tratarse como vacío.</p>
     */
    public static String vacioANulo(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    /**
     * Corta a lo que quepa en la columna. Se usa con datos que llegan de fuera y no tienen
     * un largo garantizado —user agent, IP reenviada—: sin esto, un agente exótico de 400
     * caracteres tumba el {@code INSERT} entero.
     */
    public static String recortar(String valor, int largoMaximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= largoMaximo ? valor : valor.substring(0, largoMaximo);
    }
}
