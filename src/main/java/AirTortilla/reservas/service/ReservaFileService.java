package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoDetalleDto;
import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.LineaReservaDto;
import AirTortilla.reservas.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Servicio encargado de gestionar los archivos generados y proporcionar
 * las consultas en modalidad TOTAL y en modalidad LÍNEA A LÍNEA.
 */
@Service
public class ReservaFileService {

    private static final Logger log = LoggerFactory.getLogger(ReservaFileService.class);
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^AirTortilla_([A-Za-z0-9]+)(?:_(\\d{4})_(\\d{2})_(\\d{2}))?\\.csv$");

    private final ReservaExportService exportService;
    private final ReservaParserService parserService;

    public ReservaFileService(ReservaExportService exportService, ReservaParserService parserService) {
        this.exportService = exportService;
        this.parserService = parserService;
    }

    /**
     * Valida el nombre del archivo para prevenir vulnerabilidades de Path Traversal.
     */
    public Path resolverArchivo(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.contains("..") || nombreArchivo.contains("/") || nombreArchivo.contains("\\")) {
            throw new IllegalArgumentException("Nombre de archivo inválido o peligroso: " + nombreArchivo);
        }
        Path path = exportService.getDirectorioSalida().resolve(nombreArchivo).normalize();
        if (!path.startsWith(exportService.getDirectorioSalida())) {
            throw new SecurityException("Acceso denegado a ruta no autorizada");
        }
        if (!Files.exists(path)) {
            throw new NoSuchElementException("El archivo no existe: " + nombreArchivo);
        }
        return path;
    }

    /**
     * Lista todos los archivos generados con sus metadatos.
     */
    public List<ArchivoReservaInfo> listarArchivos() {
        Path dir = exportService.getDirectorioSalida();
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }

        List<ArchivoReservaInfo> lista = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("AirTortilla_") && p.toString().endsWith(".csv"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .forEach(p -> {
                        String nombre = p.getFileName().toString();
                        Matcher m = FILENAME_PATTERN.matcher(nombre);
                        String pais = "XX";
                        LocalDate fecha = LocalDate.now();
                        if (m.matches()) {
                            pais = m.group(1);
                            if (m.group(2) != null) {
                                fecha = LocalDate.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
                            }
                        }

                        long tamano = 0;
                        LocalDateTime fechaGen = LocalDateTime.now();
                        int totalReservas = 0;

                        try {
                            tamano = Files.size(p);
                            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                            fechaGen = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(), ZoneId.systemDefault());

                            // Contar líneas de datos (total líneas - 1 de cabecera)
                            try (Stream<String> lines = Files.lines(p, StandardCharsets.UTF_8)) {
                                long count = lines.count();
                                totalReservas = (int) Math.max(0, count - 1);
                            }
                        } catch (IOException ignored) {}

                        lista.add(new ArchivoReservaInfo(nombre, pais, fecha, totalReservas, tamano, fechaGen));
                    });
        } catch (IOException e) {
            log.error("Error al listar archivos generados", e);
        }

        return lista;
    }

    /**
     * Consulta un archivo en MODALIDAD TOTAL.
     */
    public ArchivoDetalleDto consultarArchivoTotal(String nombreArchivo) {
        Path path = resolverArchivo(nombreArchivo);
        try {
            String contenidoRaw = Files.readString(path, StandardCharsets.UTF_8);
            List<Reserva> reservas = parserService.parsearCsvTexto(contenidoRaw);
            List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservas);

            Matcher m = FILENAME_PATTERN.matcher(nombreArchivo);
            String pais = "XX";
            LocalDate fecha = LocalDate.now();
            if (m.matches()) {
                pais = m.group(1);
                fecha = LocalDate.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            }

            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            LocalDateTime fechaGen = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(), ZoneId.systemDefault());

            ArchivoReservaInfo info = new ArchivoReservaInfo(
                    nombreArchivo,
                    pais,
                    fecha,
                    reservas.size(),
                    Files.size(path),
                    fechaGen
            );

            return new ArchivoDetalleDto(info, reservas, contenidoRaw, grupos);
        } catch (IOException e) {
            log.error("Error al leer archivo total: {}", nombreArchivo, e);
            throw new RuntimeException("Error al leer el archivo en modalidad total: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta un archivo en MODALIDAD LÍNEA A LÍNEA.
     * @param numeroLinea Índice 1-based de la fila de datos (sin contar la cabecera).
     */
    public LineaReservaDto consultarArchivoLinea(String nombreArchivo, int numeroLinea) {
        Path path = resolverArchivo(nombreArchivo);

        List<String> todasLasLineas;
        try {
            todasLasLineas = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error al leer líneas del archivo: " + e.getMessage(), e);
        }

        if (todasLasLineas.isEmpty()) {
            throw new NoSuchElementException("El archivo está vacío");
        }

        String cabecera = todasLasLineas.getFirst();
        List<String> lineasDatos = todasLasLineas.subList(1, todasLasLineas.size());
        int totalLineas = lineasDatos.size();

        if (totalLineas == 0) {
            throw new NoSuchElementException("El archivo no contiene filas de datos");
        }

        // Asegurar que el número de línea esté en rango válido [1, totalLineas]
        if (numeroLinea < 1) {
            numeroLinea = 1;
        } else if (numeroLinea > totalLineas) {
            numeroLinea = totalLineas;
        }

        String lineaRaw = lineasDatos.get(numeroLinea - 1);

        // Parsear la fila individual junto con la cabecera para obtener el objeto Reserva
        String csvParaFila = cabecera + System.lineSeparator() + lineaRaw;
        List<Reserva> listaSingle = parserService.parsearCsvTexto(csvParaFila);
        Reserva reserva = !listaSingle.isEmpty() ? listaSingle.getFirst() : null;

        // Verificar si es coincidente en el archivo completo
        List<Reserva> todasReservas = parserService.parsearCsvTexto(String.join(System.lineSeparator(), todasLasLineas));
        List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(todasReservas);

        boolean esCoincidente = false;
        int totalCoincidentes = 1;

        if (reserva != null) {
            String clave = reserva.getClaveCoincidencia();
            for (GrupoCoincidenciaDto g : grupos) {
                if (g.getClave().equals(clave)) {
                    esCoincidente = true;
                    totalCoincidentes = g.getTotalCoincidentes();
                    break;
                }
            }
        }

        LineaReservaDto dto = new LineaReservaDto();
        dto.setNombreArchivo(nombreArchivo);
        dto.setNumeroLinea(numeroLinea);
        dto.setTotalLineas(totalLineas);
        dto.setLineaRaw(lineaRaw);
        dto.setReserva(reserva);
        dto.setTieneAnterior(numeroLinea > 1);
        dto.setTieneSiguiente(numeroLinea < totalLineas);
        dto.setEsCoincidente(esCoincidente);
        dto.setTotalCoincidentes(totalCoincidentes);

        return dto;
    }

    /**
     * Limpia los archivos generados en el directorio de salida.
     */
    public int limpiarArchivos() {
        Path dir = exportService.getDirectorioSalida();
        if (!Files.exists(dir)) {
            return 0;
        }

        int borrados = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().startsWith("AirTortilla_") && p.toString().endsWith(".csv"))
                    .toList();
            for (Path p : files) {
                Files.deleteIfExists(p);
                borrados++;
            }
        } catch (IOException e) {
            log.error("Error al limpiar archivos generados", e);
        }
        return borrados;
    }
}
