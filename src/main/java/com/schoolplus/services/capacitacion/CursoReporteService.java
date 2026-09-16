package com.schoolplus.services.capacitacion;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.CursoInscripcion;
import com.schoolplus.repository.CursoInscripcionRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.apache.poi.ss.usermodel.Cell;
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

/** El consolidado de finalización de cursos que pide Talento Humano — una fila por (curso, colaborador). */
@Service
public class CursoReporteService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] ENCABEZADOS = {
            "Curso", "Tipo", "Colaborador", "Video completado", "Quiz aprobado",
            "Mejor nota", "Intentos", "Completado en"
    };

    private final CursoInscripcionRepository inscripcionRepository;
    private final CapacidadService capacidades;

    public CursoReporteService(CursoInscripcionRepository inscripcionRepository, CapacidadService capacidades) {
        this.inscripcionRepository = inscripcionRepository;
        this.capacidades = capacidades;
    }

    @Transactional(readOnly = true)
    public byte[] generarConsolidado() {
        if (!capacidades.puede(Capacidades.CAPACITACION_ADMINISTRAR)) {
            throw new IllegalArgumentException("No tienes permiso para generar este reporte.");
        }

        List<CursoInscripcion> inscripciones = inscripcionRepository.reporteFinalizacion();

        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("Capacitaciones");
            escribirEncabezado(hoja);

            int fila = 1;
            for (CursoInscripcion inscripcion : inscripciones) {
                escribirFila(hoja, fila++, inscripcion);
            }
            for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                hoja.autoSizeColumn(columna);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte de capacitaciones.", e);
        }
    }

    private void escribirEncabezado(XSSFSheet hoja) {
        Row fila = hoja.createRow(0);
        for (int i = 0; i < ENCABEZADOS.length; i++) {
            fila.createCell(i).setCellValue(ENCABEZADOS[i]);
        }
    }

    private void escribirFila(XSSFSheet hoja, int numeroFila, CursoInscripcion inscripcion) {
        Row fila = hoja.createRow(numeroFila);
        int c = 0;
        fila.createCell(c++).setCellValue(inscripcion.getCurso().getTitulo());
        fila.createCell(c++).setCellValue(inscripcion.getCurso().getTipo().name());
        fila.createCell(c++).setCellValue(inscripcion.getUsuario().getNombre());
        fila.createCell(c++).setCellValue(inscripcion.getVideoCompletadoEn() != null ? "Sí" : "No");
        fila.createCell(c++).setCellValue(inscripcion.isQuizAprobado() ? "Sí" : "No");

        Cell notaCelda = fila.createCell(c++);
        if (inscripcion.getMejorNota() != null) {
            notaCelda.setCellValue(inscripcion.getMejorNota().doubleValue());
        }

        fila.createCell(c++).setCellValue(inscripcion.getIntentos());
        fila.createCell(c).setCellValue(inscripcion.getCompletadoEn() != null
                ? inscripcion.getCompletadoEn().format(FORMATO_FECHA) : "");
    }
}
