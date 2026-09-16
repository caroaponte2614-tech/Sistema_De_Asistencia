package com.schoolplus.services.documentosinstitucionales;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.DocumentoInstitucionalVista;
import com.schoolplus.repository.DocumentoInstitucionalVistaRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El consolidado que le da sentido al chulo: quién leyó qué documento institucional y
 * cuándo. Gate con {@code REPORTE_GENERAR} —no con {@code DOCUMENTO_INSTITUCIONAL_ADMINISTRAR}—
 * porque es exactamente la capacidad que el catálogo ya tenía reservada, sin usar, para esto.
 */
@Service
public class DocumentoInstitucionalReporteService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] ENCABEZADOS = {
            "Documento", "Categoría", "Colaborador", "Primera vez", "Última vez", "Veces visto"
    };

    private final DocumentoInstitucionalVistaRepository vistaRepository;
    private final CapacidadService capacidades;

    public DocumentoInstitucionalReporteService(DocumentoInstitucionalVistaRepository vistaRepository,
                                                CapacidadService capacidades) {
        this.vistaRepository = vistaRepository;
        this.capacidades = capacidades;
    }

    @Transactional(readOnly = true)
    public byte[] generarConsolidado() {
        if (!capacidades.puede(Capacidades.REPORTE_GENERAR)) {
            throw new IllegalArgumentException("No tienes permiso para generar este reporte.");
        }

        List<DocumentoInstitucionalVista> vistas = vistaRepository.reporteCompleto();

        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("Documentos institucionales");
            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                encabezado.createCell(i).setCellValue(ENCABEZADOS[i]);
            }

            int fila = 1;
            for (DocumentoInstitucionalVista vista : vistas) {
                Row filaDatos = hoja.createRow(fila++);
                int c = 0;
                filaDatos.createCell(c++).setCellValue(vista.getDocumento().getTitulo());
                filaDatos.createCell(c++).setCellValue(
                        vista.getDocumento().getCategoria() == null ? "" : vista.getDocumento().getCategoria());
                filaDatos.createCell(c++).setCellValue(vista.getUsuario().getNombre());
                filaDatos.createCell(c++).setCellValue(vista.getPrimeraVezEn().format(FORMATO_FECHA));
                filaDatos.createCell(c++).setCellValue(vista.getUltimaVezEn().format(FORMATO_FECHA));
                filaDatos.createCell(c).setCellValue(vista.getVecesVisto());
            }
            for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                hoja.autoSizeColumn(columna);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte de documentos institucionales.", e);
        }
    }
}
