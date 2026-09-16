package com.schoolplus.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generador de UUID versión 7 (RFC 9562): 48 bits de marca de tiempo en milisegundos
 * seguidos de 74 bits aleatorios.
 *
 * <p>Existe por la decisión D-006: las claves primarias son UUID y no enteros
 * autoincrementales, porque los ids de los tres sistemas de origen colisionan entre sí.
 * La versión 7 —y no la 4 de {@link UUID#randomUUID()}— porque es ordenable en el tiempo:
 * las inserciones caen al final del índice agrupado de InnoDB en vez de fragmentarlo.</p>
 *
 * <p>Se implementa a mano, con 20 líneas, en vez de traer una dependencia entera. Java 21
 * no genera UUID v7 de fábrica.</p>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generar() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        long milis = System.currentTimeMillis();

        // 48 bits de marca de tiempo, big-endian, en los bytes 0..5.
        bytes[0] = (byte) (milis >>> 40);
        bytes[1] = (byte) (milis >>> 32);
        bytes[2] = (byte) (milis >>> 24);
        bytes[3] = (byte) (milis >>> 16);
        bytes[4] = (byte) (milis >>> 8);
        bytes[5] = (byte) milis;

        // Nibble de versión = 7 y dos bits de variante = 0b10 (RFC 4122).
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long alta = 0;
        long baja = 0;
        for (int i = 0; i < 8; i++) {
            alta = (alta << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            baja = (baja << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(alta, baja);
    }

    /** Atajo para las claves primarias, que en el esquema son {@code CHAR(36)}. */
    public static String nuevoId() {
        return generar().toString();
    }
}
