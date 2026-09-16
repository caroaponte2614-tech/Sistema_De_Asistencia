package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.certificados.CertificadoLaboralService;
import com.schoolplus.services.certificados.CertificadoVerificacion;
import com.schoolplus.services.documentos.ArchivoDescargado;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Certificado laboral: cualquiera genera el suyo (autoservicio, sin aprobación);
 * Talento Humano puede generarlo a nombre de otra persona. La página de verificación
 * pública ({@code /certificados/verificar/{codigo}}) no usa el layout con sidebar —
 * es, como el login, una pantalla standalone, y no lleva {@code @PreAuthorize}.
 */
@Controller
public class CertificadoLaboralController {

    private final CertificadoLaboralService certificadoService;
    private final UsuarioRepository usuarioRepository;

    public CertificadoLaboralController(CertificadoLaboralService certificadoService,
                                        UsuarioRepository usuarioRepository) {
        this.certificadoService = certificadoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/mi-espacio/certificado-laboral")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_SOLICITAR + "')")
    public String misCertificados(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("certificados", certificadoService.misCertificados(yo));
        modelo.addAttribute("seccion", "certificado-laboral");
        return "certificados/mis-certificados";
    }

    @PostMapping("/mi-espacio/certificado-laboral")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_SOLICITAR + "')")
    public String generarPropio(@Actor Usuario yo, RedirectAttributes redirect) {
        try {
            certificadoService.generar(yo, yo.getId());
            redirect.addFlashAttribute("aviso", "Certificado generado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-espacio/certificado-laboral";
    }

    @GetMapping("/mi-espacio/certificado-laboral/{id}/descargar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_SOLICITAR + "')")
    public ResponseEntity<InputStreamResource> descargarPropio(@Actor Usuario yo, @PathVariable("id") String id) {
        return descargar(yo, id);
    }

    @GetMapping("/personas/{usuarioId}/certificado-laboral")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS + "')")
    public String certificadosDe(@PathVariable("usuarioId") String usuarioId, Model modelo) {
        Usuario colaborador = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));
        modelo.addAttribute("colaborador", colaborador);
        modelo.addAttribute("certificados", certificadoService.misCertificados(colaborador));
        modelo.addAttribute("seccion", "equipo");
        return "certificados/de-un-colaborador";
    }

    @PostMapping("/personas/{usuarioId}/certificado-laboral")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS + "')")
    public String generarParaOtro(@PathVariable("usuarioId") String usuarioId, @Actor Usuario actor,
                                  RedirectAttributes redirect) {
        try {
            certificadoService.generar(actor, usuarioId);
            redirect.addFlashAttribute("aviso", "Certificado generado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personas/" + usuarioId + "/certificado-laboral";
    }

    @GetMapping("/personas/{usuarioId}/certificado-laboral/{id}/descargar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS + "')")
    public ResponseEntity<InputStreamResource> descargarDeOtro(@Actor Usuario actor, @PathVariable("id") String id) {
        return descargar(actor, id);
    }

    // ── Verificación pública ─────────────────────────────────────────────────

    @GetMapping("/certificados/verificar/{codigo}")
    public String verificar(@PathVariable("codigo") String codigo, Model modelo) {
        CertificadoVerificacion resultado = certificadoService.verificar(codigo).orElse(null);
        modelo.addAttribute("resultado", resultado);
        modelo.addAttribute("codigo", codigo);
        return "certificados/verificacion-publica";
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private ResponseEntity<InputStreamResource> descargar(Usuario actor, String certificadoId) {
        ArchivoDescargado archivo;
        try {
            archivo = certificadoService.descargar(actor, certificadoId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .body(new InputStreamResource(archivo.contenido()));
    }
}
