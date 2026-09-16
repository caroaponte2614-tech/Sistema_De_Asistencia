package com.schoolplus.services.cuenta;

import com.schoolplus.model.UsuarioFoto;
import com.schoolplus.repository.UsuarioFotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;

/**
 * Foto de perfil de "Mi cuenta". Guardada en la base de datos (V9): a la escala de este
 * proyecto —máximo 100 usuarios síncronos, decisión del usuario 2026-08-28— montar un
 * almacenamiento de archivos aparte para esto es complejidad que no se paga sola.
 */
@Service
public class CuentaService {

    /** JPEG, PNG y WEBP: lo que cualquier cámara de celular o recorte produce hoy. */
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long TAMANO_MAXIMO_BYTES = 3L * 1024 * 1024; // 3 MB

    private final UsuarioFotoRepository fotoRepository;

    public CuentaService(UsuarioFotoRepository fotoRepository) {
        this.fotoRepository = fotoRepository;
    }

    @Transactional(readOnly = true)
    public boolean tieneFoto(String usuarioId) {
        return usuarioId != null && fotoRepository.existsById(usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioFoto> obtenerFoto(String usuarioId) {
        return fotoRepository.findById(usuarioId);
    }

    @Transactional
    public void subirFoto(String usuarioId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Elige una imagen antes de subirla.");
        }
        String tipo = archivo.getContentType();
        if (tipo == null || !TIPOS_PERMITIDOS.contains(tipo)) {
            throw new IllegalArgumentException("La foto debe ser JPEG, PNG o WEBP.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("La foto no puede pesar más de 3 MB.");
        }

        UsuarioFoto foto = fotoRepository.findById(usuarioId).orElseGet(UsuarioFoto::new);
        foto.setUsuarioId(usuarioId);
        foto.setTipoContenido(tipo);
        try {
            foto.setContenido(archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen enviada.", e);
        }
        fotoRepository.save(foto);
    }

    @Transactional
    public void eliminarFoto(String usuarioId) {
        fotoRepository.deleteById(usuarioId);
    }
}
