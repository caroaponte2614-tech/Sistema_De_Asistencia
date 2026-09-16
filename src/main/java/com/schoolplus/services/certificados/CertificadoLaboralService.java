package com.schoolplus.services.certificados;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.CertificadoLaboral;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CertificadoLaboralRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.documentos.FormatoArchivo;
import com.schoolplus.services.documentos.PoliticaArchivos;
import com.schoolplus.services.documentos.TipoArchivo;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * Certificado laboral (§ certificados): generación inmediata y automática, sin
 * aprobación — ver el porqué en la migración V21. El PDF se genera una vez y queda
 * guardado; el código de verificación de su QR siempre apunta al mismo documento.
 */
@Service
public class CertificadoLaboralService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoLaboralService.class);
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin 0/O ni 1/I, se confunden
    private static final int LARGO_CODIGO = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificadoLaboralRepository certificadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;
    private final CertificadoLaboralPdfBuilder pdfBuilder;
    private final String baseUrl;

    public CertificadoLaboralService(CertificadoLaboralRepository certificadoRepository,
                                     UsuarioRepository usuarioRepository,
                                     DocumentoAlmacenamiento almacenamiento,
                                     CapacidadService capacidades,
                                     CertificadoLaboralPdfBuilder pdfBuilder,
                                     @Value("${app.base-url}") String baseUrl) {
        this.certificadoRepository = certificadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
        this.pdfBuilder = pdfBuilder;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CertificadoLaboral generar(Usuario actor, String usuarioObjetivoId) {
        boolean puede = capacidades.puedeSobre(Capacidades.CERTIFICADO_LABORAL_SOLICITAR, usuarioObjetivoId)
                || capacidades.puedeSobre(Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS, usuarioObjetivoId);
        if (!puede) {
            throw new IllegalArgumentException("No puedes generar el certificado laboral de esa persona.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioObjetivoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));

        CertificadoLaboral certificado = new CertificadoLaboral();
        certificado.setId(UuidV7.nuevoId());
        certificado.setUsuario(usuario);
        certificado.setSolicitadoPor(actor);
        certificado.setCodigoVerificacion(codigoUnico());

        String urlVerificacion = baseUrl + "/certificados/verificar/" + certificado.getCodigoVerificacion();
        byte[] pdf = pdfBuilder.generar(usuario, certificado.getCodigoVerificacion(), urlVerificacion);

        String clave = PoliticaArchivos.nuevaClave(TipoArchivo.CERTIFICADO, FormatoArchivo.PDF);
        almacenamiento.subir(clave, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        certificado.setPdfClave(clave);

        certificadoRepository.save(certificado);
        log.info("Certificado laboral generado para {} (solicitado por {})", usuario.getEmail(), actor.getEmail());
        return certificado;
    }

    @Transactional(readOnly = true)
    public List<CertificadoLaboral> misCertificados(Usuario colaborador) {
        return certificadoRepository.misCertificados(colaborador.getId());
    }

    @Transactional(readOnly = true)
    public ArchivoDescargado descargar(Usuario actor, String certificadoId) {
        CertificadoLaboral certificado = certificadoRepository.findById(certificadoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese certificado no existe."));
        boolean puede = capacidades.puedeSobre(Capacidades.CERTIFICADO_LABORAL_SOLICITAR, certificado.getUsuario().getId())
                || capacidades.puedeSobre(Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS, certificado.getUsuario().getId());
        if (!puede) {
            throw new IllegalArgumentException("No puedes descargar este certificado.");
        }
        return new ArchivoDescargado(
                almacenamiento.descargar(certificado.getPdfClave()),
                "certificado-laboral.pdf", "application/pdf", 0);
    }

    /**
     * Datos enmascarados para la página pública de verificación: confirma validez sin
     * exponer todo — el caso de uso real es alguien escaneando el QR de un papel
     * impreso, y ese QR no debería ser un vector de fuga de datos personales.
     */
    @Transactional(readOnly = true)
    public Optional<CertificadoVerificacion> verificar(String codigo) {
        return certificadoRepository.findByCodigoVerificacion(codigo)
                .map(c -> new CertificadoVerificacion(enmascarar(c.getUsuario().getNombre()),
                        enmascararCedula(c.getUsuario().getCedula()), c.getEmitidoEn()));
    }

    private String codigoUnico() {
        for (int intento = 0; intento < 5; intento++) {
            String candidato = generarCodigo();
            if (certificadoRepository.findByCodigoVerificacion(candidato).isEmpty()) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de verificación único.");
    }

    private String generarCodigo() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(ALFABETO_CODIGO.charAt(RANDOM.nextInt(ALFABETO_CODIGO.length())));
        }
        return codigo.toString();
    }

    private String enmascarar(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "—";
        }
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder resultado = new StringBuilder();
        for (String parte : partes) {
            if (!resultado.isEmpty()) {
                resultado.append(' ');
            }
            resultado.append(parte.charAt(0)).append("***");
        }
        return resultado.toString();
    }

    private String enmascararCedula(String cedula) {
        if (cedula == null || cedula.length() < 4) {
            return "****";
        }
        return "****" + cedula.substring(cedula.length() - 4);
    }
}
