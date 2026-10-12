package com.schoolplus.services.asistencia;

import com.schoolplus.dto.FiltroAsistencia;
import com.schoolplus.dto.ReporteAsistencia;
import com.schoolplus.dto.ResumenAsistencia;
import com.schoolplus.dto.ResumenSesion;
import com.schoolplus.model.Ambito;
import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoAsistencia;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AsistenciaRegistroRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Los reportes de asistencia: por sesión, por clase, por estudiante y por rango de fechas,
 * con porcentajes y exportación a Excel y CSV.
 *
 * <p>Solo entran sesiones <strong>cerradas</strong>: mientras una sigue abierta todavía no
 * existen sus ausentes, y contarla daría porcentajes que se mueven solos.</p>
 *
 * <p>El alcance sale de la matriz: con {@code ASISTENCIA_REPORTE} en TODOS se ve la
 * institución entera; en PROPIO, solo las clases que dicta quien pregunta.</p>
 */
@Service
public class AsistenciaReporteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] ENCABEZADOS_DETALLE = {
            "Fecha", "Hora de la clase", "Clase", "Asignatura", "Profesor", "Estudiante",
            "Estado", "Método", "Registrado", "Observación"
    };
    private static final String[] ENCABEZADOS_RESUMEN = {
            "Nombre", "Detalle", "Presentes", "Tardes", "Ausentes", "Justificados", "Sesiones", "% asistencia"
    };

    private final AsistenciaRegistroRepository registroRepository;
    private final CapacidadService capacidades;
    private final Clock reloj;

    public AsistenciaReporteService(AsistenciaRegistroRepository registroRepository,
                                    CapacidadService capacidades,
                                    Clock reloj) {
        this.registroRepository = registroRepository;
        this.capacidades = capacidades;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ReporteAsistencia generar(Usuario actor, FiltroAsistencia filtroCrudo) {
        FiltroAsistencia filtro = filtroCrudo.conValoresPorDefecto(reloj);
        String profesorId = alcanceDe(actor);

        List<AsistenciaRegistro> registros = registroRepository.paraReporte(
                filtro.desde().atStartOfDay(),
                filtro.hasta().plusDays(1).atStartOfDay(),
                filtro.claseId(),
                filtro.estudianteId(),
                profesorId);

        return new ReporteAsistencia(
                filtro,
                contar("total", "Total", "", registros),
                agrupar(registros, r -> r.getEstudiante().getId(),
                        r -> r.getEstudiante().getNombre(),
                        r -> r.getEstudiante().getEmail()),
                agrupar(registros, r -> r.getSesion().getClase().getId(),
                        r -> r.getSesion().getClase().etiqueta(),
                        r -> r.getSesion().getClase().getProfesor().getNombre()),
                porSesion(registros),
                registros);
    }

    /** Un libro con cuatro hojas: totales por estudiante, por clase, por sesión y el detalle. */
    @Transactional(readOnly = true)
    public byte[] excel(Usuario actor, FiltroAsistencia filtro) {
        ReporteAsistencia reporte = generar(actor, filtro);
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            hojaDeResumen(libro.createSheet("Por estudiante"), reporte.porEstudiante());
            hojaDeResumen(libro.createSheet("Por clase"), reporte.porClase());
            hojaDeSesiones(libro.createSheet("Por sesión"), reporte.porSesion());
            hojaDeDetalle(libro.createSheet("Detalle"), reporte.detalle());

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte de asistencia.", e);
        }
    }

    /**
     * El detalle en CSV. Lleva marca de orden de bytes (BOM) porque, sin ella, Excel en
     * Windows abre el archivo en su codificación local y parte los acentos.
     */
    @Transactional(readOnly = true)
    public byte[] csv(Usuario actor, FiltroAsistencia filtro) {
        ReporteAsistencia reporte = generar(actor, filtro);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(";", ENCABEZADOS_DETALLE)).append('\n');
        for (AsistenciaRegistro registro : reporte.detalle()) {
            csv.append(String.join(";", celdasDetalle(registro))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Cálculo ──────────────────────────────────────────────────────────────

    private String alcanceDe(Usuario actor) {
        Ambito ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_REPORTE)
                .orElseThrow(() -> new IllegalArgumentException("No tienes permiso para ver reportes de asistencia."));
        // PROPIO queda acotado a las clases del actor.
        return ambito == Ambito.TODOS ? null : actor.getId();
    }

    private List<ResumenAsistencia> agrupar(List<AsistenciaRegistro> registros,
                                            Function<AsistenciaRegistro, String> clave,
                                            Function<AsistenciaRegistro, String> etiqueta,
                                            Function<AsistenciaRegistro, String> detalle) {
        Map<String, List<AsistenciaRegistro>> porClave = new LinkedHashMap<>();
        for (AsistenciaRegistro registro : registros) {
            porClave.computeIfAbsent(clave.apply(registro), k -> new ArrayList<>()).add(registro);
        }
        return porClave.entrySet().stream()
                .map(entrada -> contar(entrada.getKey(),
                        etiqueta.apply(entrada.getValue().get(0)),
                        detalle.apply(entrada.getValue().get(0)),
                        entrada.getValue()))
                .sorted((a, b) -> a.etiqueta().compareToIgnoreCase(b.etiqueta()))
                .toList();
    }

    private List<ResumenSesion> porSesion(List<AsistenciaRegistro> registros) {
        Map<String, List<AsistenciaRegistro>> porSesion = new LinkedHashMap<>();
        for (AsistenciaRegistro registro : registros) {
            porSesion.computeIfAbsent(registro.getSesion().getId(), k -> new ArrayList<>()).add(registro);
        }
        List<ResumenSesion> filas = new ArrayList<>();
        porSesion.forEach((sesionId, delGrupo) -> {
            var sesion = delGrupo.get(0).getSesion();
            filas.add(new ResumenSesion(
                    sesionId,
                    sesion.getClase().etiqueta(),
                    sesion.getClase().getProfesor().getNombre(),
                    sesion.getAbiertaEn().format(FECHA),
                    sesion.getAbiertaEn().format(HORA),
                    contar(sesionId, sesion.getClase().etiqueta(), "", delGrupo)));
        });
        return filas;
    }

    private ResumenAsistencia contar(String id, String etiqueta, String detalle, List<AsistenciaRegistro> registros) {
        long presentes = cuantos(registros, EstadoAsistencia.PRESENTE);
        long tardes = cuantos(registros, EstadoAsistencia.TARDE);
        long ausentes = cuantos(registros, EstadoAsistencia.AUSENTE);
        long justificados = cuantos(registros, EstadoAsistencia.JUSTIFICADO);
        return new ResumenAsistencia(id, etiqueta, detalle, presentes, tardes, ausentes, justificados);
    }

    private long cuantos(List<AsistenciaRegistro> registros, EstadoAsistencia estado) {
        return registros.stream().filter(r -> r.getEstado() == estado).count();
    }

    // ── Excel ────────────────────────────────────────────────────────────────

    private void hojaDeResumen(XSSFSheet hoja, List<ResumenAsistencia> resumenes) {
        escribirEncabezado(hoja, ENCABEZADOS_RESUMEN);
        int numeroFila = 1;
        for (ResumenAsistencia resumen : resumenes) {
            Row fila = hoja.createRow(numeroFila++);
            int c = 0;
            fila.createCell(c++).setCellValue(resumen.etiqueta());
            fila.createCell(c++).setCellValue(resumen.detalle());
            fila.createCell(c++).setCellValue(resumen.presentes());
            fila.createCell(c++).setCellValue(resumen.tardes());
            fila.createCell(c++).setCellValue(resumen.ausentes());
            fila.createCell(c++).setCellValue(resumen.justificados());
            fila.createCell(c++).setCellValue(resumen.total());
            fila.createCell(c).setCellValue(resumen.porcentajeLegible());
        }
        autoajustar(hoja, ENCABEZADOS_RESUMEN.length);
    }

    private void hojaDeSesiones(XSSFSheet hoja, List<ResumenSesion> sesiones) {
        String[] encabezados = {"Fecha", "Hora", "Clase", "Profesor", "Presentes", "Tardes",
                "Ausentes", "Justificados", "Registros", "% asistencia"};
        escribirEncabezado(hoja, encabezados);
        int numeroFila = 1;
        for (ResumenSesion sesion : sesiones) {
            Row fila = hoja.createRow(numeroFila++);
            int c = 0;
            fila.createCell(c++).setCellValue(sesion.fecha());
            fila.createCell(c++).setCellValue(sesion.hora());
            fila.createCell(c++).setCellValue(sesion.clase());
            fila.createCell(c++).setCellValue(sesion.profesor());
            fila.createCell(c++).setCellValue(sesion.conteo().presentes());
            fila.createCell(c++).setCellValue(sesion.conteo().tardes());
            fila.createCell(c++).setCellValue(sesion.conteo().ausentes());
            fila.createCell(c++).setCellValue(sesion.conteo().justificados());
            fila.createCell(c++).setCellValue(sesion.conteo().total());
            fila.createCell(c).setCellValue(sesion.conteo().porcentajeLegible());
        }
        autoajustar(hoja, encabezados.length);
    }

    private void hojaDeDetalle(XSSFSheet hoja, List<AsistenciaRegistro> registros) {
        escribirEncabezado(hoja, ENCABEZADOS_DETALLE);
        int numeroFila = 1;
        for (AsistenciaRegistro registro : registros) {
            Row fila = hoja.createRow(numeroFila++);
            String[] celdas = celdasDetalle(registro);
            for (int c = 0; c < celdas.length; c++) {
                fila.createCell(c).setCellValue(celdas[c]);
            }
        }
        autoajustar(hoja, ENCABEZADOS_DETALLE.length);
    }

    private String[] celdasDetalle(AsistenciaRegistro registro) {
        var sesion = registro.getSesion();
        return new String[]{
                sesion.getAbiertaEn().format(FECHA),
                sesion.getHoraReferencia().format(HORA),
                limpiar(sesion.getClase().etiqueta()),
                limpiar(sesion.getClase().getAsignatura()),
                limpiar(sesion.getClase().getProfesor().getNombre()),
                limpiar(registro.getEstudiante().getNombre()),
                registro.getEstado().etiqueta(),
                registro.getMetodo().etiqueta(),
                registro.getRegistradoEn().format(HORA),
                limpiar(registro.getObservacion())
        };
    }

    /** Quita el separador y los saltos de línea: con CSV por punto y coma, basta con eso. */
    private String limpiar(String valor) {
        return valor == null ? "" : valor.replace(";", ",").replaceAll("[\\r\\n]+", " ");
    }

    private void escribirEncabezado(XSSFSheet hoja, String[] encabezados) {
        Row fila = hoja.createRow(0);
        for (int i = 0; i < encabezados.length; i++) {
            Cell celda = fila.createCell(i);
            celda.setCellValue(encabezados[i]);
        }
    }

    private void autoajustar(XSSFSheet hoja, int columnas) {
        for (int columna = 0; columna < columnas; columna++) {
            hoja.autoSizeColumn(columna);
        }
    }
}
