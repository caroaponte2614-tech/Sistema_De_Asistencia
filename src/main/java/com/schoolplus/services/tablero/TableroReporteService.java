package com.schoolplus.services.tablero;

import com.schoolplus.model.Tablero;
import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
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
 * El reporte descargable del tablero de un área: una fila por tarjeta, con el estado
 * ACTUAL de cada una.
 *
 * <p>No hay historial de movimientos en el tablero (§4.3 lo diseñó como estado, no como
 * bitácora), así que esto es una foto de hoy —columna en la que está cada tarea ahora—, no
 * un reporte de actividad por período.</p>
 */
@Service
public class TableroReporteService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] ENCABEZADOS = {
            "Título", "Columna", "Prioridad", "Etiqueta", "Fecha límite",
            "Creado/asignado por", "Creado en"
    };

    private final TableroService tableroService;

    public TableroReporteService(TableroService tableroService) {
        this.tableroService = tableroService;
    }

    /** Excel con las tareas actuales del tablero del área {@code areaId}. */
    @Transactional(readOnly = true)
    public byte[] generarReporteDeArea(Usuario actor, String areaId) {
        Tablero tablero = tableroService.tableroDeAreaValidado(actor, areaId);
        List<TableroTarjeta> tarjetas = tableroService.tarjetasDe(tablero.getId());

        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("Tareas");
            escribirEncabezado(hoja);

            int fila = 1;
            for (TableroTarjeta tarjeta : tarjetas) {
                escribirFila(hoja, fila++, tarjeta);
            }
            for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                hoja.autoSizeColumn(columna);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte del tablero.", e);
        }
    }

    private void escribirEncabezado(XSSFSheet hoja) {
        Row fila = hoja.createRow(0);
        for (int i = 0; i < ENCABEZADOS.length; i++) {
            fila.createCell(i).setCellValue(ENCABEZADOS[i]);
        }
    }

    private void escribirFila(XSSFSheet hoja, int numeroFila, TableroTarjeta tarjeta) {
        Row fila = hoja.createRow(numeroFila);
        int c = 0;
        fila.createCell(c++).setCellValue(tarjeta.getTitulo());
        fila.createCell(c++).setCellValue(tarjeta.getColumna().getNombre());
        fila.createCell(c++).setCellValue(tarjeta.getPrioridad().name());
        fila.createCell(c++).setCellValue(tarjeta.getEtiqueta() != null ? tarjeta.getEtiqueta() : "");
        fila.createCell(c++).setCellValue(tarjeta.getFechaLimite() != null
                ? tarjeta.getFechaLimite().format(FORMATO_FECHA) : "");
        fila.createCell(c++).setCellValue(tarjeta.getCreadoPor().getNombre());
        fila.createCell(c).setCellValue(tarjeta.getCreadoEn().format(FORMATO_FECHA));
    }
}
