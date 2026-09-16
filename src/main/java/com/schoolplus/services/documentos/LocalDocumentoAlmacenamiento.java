package com.schoolplus.services.documentos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Almacenamiento en disco local, dentro de la carpeta {@code app.storage.path}
 * (variable {@code STORAGE_PATH}, por defecto {@code ./uploads}).
 *
 * <p>Implementa la misma {@link DocumentoAlmacenamiento} que usan los servicios, así que
 * ninguno de ellos sabe dónde viven los bytes. La clave que recibe es siempre una ruta
 * relativa a la raíz ({@code videos/<uuid>.mp4}) y es lo único que se guarda en la base.</p>
 *
 * <p>Ninguna clave sale de la raíz: se normaliza y se exige que el resultado siga dentro
 * de ella, así que {@code ../} o una ruta absoluta se rechazan antes de tocar el disco
 * (protección contra path traversal). La carpeta nunca se publica como recurso estático:
 * cada archivo se sirve desde un controlador que antes comprueba permisos.</p>
 */
@Service
public class LocalDocumentoAlmacenamiento implements DocumentoAlmacenamiento {

    private static final String MIME_DESCONOCIDO = "application/octet-stream";

    private final Path raiz;

    public LocalDocumentoAlmacenamiento(@Value("${app.storage.path:./uploads}") String ruta) {
        this.raiz = Paths.get(ruta).toAbsolutePath().normalize();
    }

    @Override
    public void subir(String clave, InputStream contenido, long tamanoBytes, String mime) {
        Path destino = resolver(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Se escribe primero a un temporal de la misma carpeta y luego se mueve: un
            // lector concurrente nunca ve un archivo a medio escribir.
            Path temporal = Files.createTempFile(destino.getParent(), ".subiendo-", ".tmp");
            try {
                Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
                mover(temporal, destino);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new AlmacenamientoDocumentosException("No se pudo guardar el archivo " + clave, e);
        }
    }

    @Override
    public InputStream descargar(String clave) {
        try {
            return Files.newInputStream(resolver(clave));
        } catch (NoSuchFileException e) {
            throw new AlmacenamientoDocumentosException("No existe el archivo " + clave, e);
        } catch (IOException e) {
            throw new AlmacenamientoDocumentosException("No se pudo leer " + clave, e);
        }
    }

    @Override
    public InputStream descargar(String clave, Long offset, Long length) {
        if (offset == null && length == null) {
            return descargar(clave);
        }
        try {
            SeekableByteChannel canal = Files.newByteChannel(resolver(clave), StandardOpenOption.READ);
            canal.position(offset == null ? 0 : offset);
            InputStream flujo = Channels.newInputStream(canal);
            return length == null ? flujo : new FlujoAcotado(flujo, length);
        } catch (NoSuchFileException e) {
            throw new AlmacenamientoDocumentosException("No existe el archivo " + clave, e);
        } catch (IOException e) {
            throw new AlmacenamientoDocumentosException("No se pudo leer " + clave, e);
        }
    }

    @Override
    public InfoObjeto info(String clave) {
        Path ruta = resolver(clave);
        try {
            String mime = FormatoArchivo.deNombre(clave).map(FormatoArchivo::mime).orElse(MIME_DESCONOCIDO);
            return new InfoObjeto(Files.size(ruta), mime);
        } catch (NoSuchFileException e) {
            throw new AlmacenamientoDocumentosException("No existe el archivo " + clave, e);
        } catch (IOException e) {
            throw new AlmacenamientoDocumentosException("No se pudo consultar " + clave, e);
        }
    }

    @Override
    public void eliminar(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException e) {
            throw new AlmacenamientoDocumentosException("No se pudo borrar " + clave, e);
        }
    }

    /** La raíz absoluta ya normalizada. Visible para {@code StorageConfig} y los tests. */
    public Path raiz() {
        return raiz;
    }

    /**
     * Convierte una clave relativa en una ruta dentro de la raíz, o falla.
     *
     * <p>Se rechaza antes de normalizar lo que nunca es una clave legítima (vacía, con
     * barra invertida, absoluta, con unidad de Windows o con un byte nulo), y después de
     * normalizar se exige que la ruta siga colgando de la raíz.</p>
     */
    Path resolver(String clave) {
        if (clave == null || clave.isBlank()
                || clave.indexOf('\0') >= 0
                || clave.contains("\\")
                || clave.contains(":")
                || clave.startsWith("/")) {
            throw new AlmacenamientoDocumentosException("Clave de archivo inválida: " + clave, null);
        }
        Path ruta = raiz.resolve(clave).normalize();
        if (!ruta.startsWith(raiz) || ruta.equals(raiz)) {
            throw new AlmacenamientoDocumentosException("Clave de archivo fuera del almacenamiento: " + clave, null);
        }
        return ruta;
    }

    private static void mover(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deja leer como máximo {@code restante} bytes: lo que pide una respuesta 206. */
    private static final class FlujoAcotado extends FilterInputStream {

        private long restante;

        FlujoAcotado(InputStream flujo, long limite) {
            super(flujo);
            this.restante = limite;
        }

        @Override
        public int read() throws IOException {
            if (restante <= 0) {
                return -1;
            }
            int leido = super.read();
            if (leido >= 0) {
                restante--;
            }
            return leido;
        }

        @Override
        public int read(byte[] destino, int desde, int cuantos) throws IOException {
            if (restante <= 0) {
                return -1;
            }
            int leidos = super.read(destino, desde, (int) Math.min(cuantos, restante));
            if (leidos > 0) {
                restante -= leidos;
            }
            return leidos;
        }

        @Override
        public long skip(long cuantos) throws IOException {
            long saltados = super.skip(Math.min(cuantos, restante));
            restante -= saltados;
            return saltados;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), restante);
        }
    }
}
