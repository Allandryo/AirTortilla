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

    public record InfoNombreArchivo(String pais, LocalDate fecha) {}

    public ReservaFileService(ReservaExportService exportService, ReservaParserService parserService) {
        this.exportService = exportService;
        this.parserService = parserService;
    }

    /**
     * Valida el nombre del archivo para prevenir vulnerabilidades de Path Traversal
     * y garantiza que el archivo resida dentro del directorio autorizado.
     *
     * @param nombreArchivo Nombre simple del fichero a resolver.
     * @return Path normalizado y verificado del archivo.
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
     * Lista todos los archivos CSV generados con sus metadatos asociados.
     *
     * @return Lista ordenada cronológicamente de archivos generados.
     */
    public List<ArchivoReservaInfo> listarArchivos() {
        Path dir = exportService.getDirectorioSalida();
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(this::esArchivoCsvValido)
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .map(this::construirInfoArchivo)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            log.error("Error al listar archivos generados en {}", dir, e);
            return Collections.emptyList();
        }
    }

    private boolean esArchivoCsvValido(Path p) {
        String nombre = p.getFileName().toString();
        return nombre.startsWith(ReservaExportService.PREFIJO_ARCHIVO) && nombre.endsWith(ReservaExportService.EXTENSION_CSV);
    }

    /**
     * Construye los metadatos de un archivo CSV inspeccionando sus atributos del sistema de archivos.
     */
    private Optional<ArchivoReservaInfo> construirInfoArchivo(Path path) {
        try {
            String nombre = path.getFileName().toString();
            InfoNombreArchivo metaNombre = parsearInfoDesdeNombre(nombre);

            long tamano = Files.size(path);
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            LocalDateTime fechaGen = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(), ZoneId.systemDefault());

            int totalReservas = contarLineasDeDatos(path);

            return Optional.of(new ArchivoReservaInfo(
                    nombre,
                    metaNombre.pais(),
                    metaNombre.fecha(),
                    totalReservas,
                    tamano,
                    fechaGen
            ));
        } catch (IOException e) {
            log.warn("No se pudieron leer los atributos de {}", path, e);
            return Optional.empty();
        }
    }

    private int contarLineasDeDatos(Path path) {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            long totalLineas = lines.count();
            return (int) Math.max(0, totalLineas - 1); // Descontar fila de cabecera
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Extrae el código de país y la fecha a partir del patrón de nomenclatura:
     * AirTortilla_XX_YYYY_MM_DD.csv
     */
    public InfoNombreArchivo parsearInfoDesdeNombre(String nombreArchivo) {
        Matcher matcher = FILENAME_PATTERN.matcher(nombreArchivo);
        String pais = ReservaParserService.PAIS_DESCONOCIDO;
        LocalDate fecha = LocalDate.now();

        if (matcher.matches()) {
            pais = matcher.group(1);
            if (matcher.group(2) != null) {
                int anio = Integer.parseInt(matcher.group(2));
                int mes = Integer.parseInt(matcher.group(3));
                int dia = Integer.parseInt(matcher.group(4));
                fecha = LocalDate.of(anio, mes, dia);
            }
        }
        return new InfoNombreArchivo(pais, fecha);
    }

    /**
     * Consulta un archivo en MODALIDAD TOTAL.
     * Retorna todas las reservas parseadas, el texto CSV crudo y los grupos de coincidencias.
     */
    public ArchivoDetalleDto consultarArchivoTotal(String nombreArchivo) {
        Path path = resolverArchivo(nombreArchivo);
        try {
            String contenidoRaw = Files.readString(path, StandardCharsets.UTF_8);
            List<Reserva> reservas = parserService.parsearCsvTexto(contenidoRaw);
            List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservas);

            ArchivoReservaInfo info = construirInfoArchivo(path)
                    .orElseThrow(() -> new NoSuchElementException("No se pudo obtener información de " + nombreArchivo));

            return new ArchivoDetalleDto(info, reservas, contenidoRaw, grupos);
        } catch (IOException e) {
            log.error("Error al leer archivo total: {}", nombreArchivo, e);
            throw new RuntimeException("Error al leer el archivo en modalidad total: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta un archivo en MODALIDAD LÍNEA A LÍNEA.
     *
     * @param nombreArchivo Nombre del fichero CSV.
     * @param numeroLinea Índice 1-based de la fila de datos solicitada (sin incluir cabecera).
     * @return DTO con la línea solicitada, datos de navegación y análisis de colisión.
     */
    public LineaReservaDto consultarArchivoLinea(String nombreArchivo, int numeroLinea) {
        Path path = resolverArchivo(nombreArchivo);
        List<String> todasLasLineas = leerLineasSeguro(path);

        String cabecera = todasLasLineas.getFirst();
        List<String> lineasDatos = todasLasLineas.subList(1, todasLasLineas.size());
        int totalLineas = lineasDatos.size();

        if (totalLineas == 0) {
            throw new NoSuchElementException("El archivo no contiene filas de datos");
        }

        // Clamping: asegura que el índice esté estrictamente en el intervalo [1, totalLineas]
        int lineaAjustada = Math.clamp(numeroLinea, 1, totalLineas);
        String lineaRaw = lineasDatos.get(lineaAjustada - 1);

        // Parsear la fila requerida para obtener la entidad Reserva
        Reserva reserva = parsearFilaIndividual(cabecera, lineaRaw);

        // Analizar si esta reserva particular forma parte de una colisión de datos
        ResultadoCoincidenciaLinea colision = verificarCoincidenciaFila(reserva, todasLasLineas);

        return new LineaReservaDto(
                nombreArchivo,
                lineaAjustada,
                totalLineas,
                lineaRaw,
                reserva,
                lineaAjustada > 1,
                lineaAjustada < totalLineas,
                colision.esCoincidente(),
                colision.totalCoincidentes()
        );
    }

    private List<String> leerLineasSeguro(Path path) {
        try {
            List<String> lineas = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lineas.isEmpty()) {
                throw new NoSuchElementException("El archivo está vacío");
            }
            return lineas;
        } catch (IOException e) {
            throw new RuntimeException("Error al leer líneas del archivo: " + e.getMessage(), e);
        }
    }

    private Reserva parsearFilaIndividual(String cabecera, String lineaRaw) {
        String csvParaFila = cabecera + System.lineSeparator() + lineaRaw;
        List<Reserva> listaSingle = parserService.parsearCsvTexto(csvParaFila);
        return !listaSingle.isEmpty() ? listaSingle.getFirst() : null;
    }

    private record ResultadoCoincidenciaLinea(boolean esCoincidente, int totalCoincidentes) {}

    private ResultadoCoincidenciaLinea verificarCoincidenciaFila(Reserva reserva, List<String> todasLasLineas) {
        if (reserva == null) {
            return new ResultadoCoincidenciaLinea(false, 1);
        }

        List<Reserva> todasReservas = parserService.parsearCsvTexto(String.join(System.lineSeparator(), todasLasLineas));
        List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(todasReservas);

        String clave = reserva.getClaveCoincidencia();
        for (GrupoCoincidenciaDto g : grupos) {
            if (g.getClave().equals(clave)) {
                return new ResultadoCoincidenciaLinea(true, g.getTotalCoincidentes());
            }
        }
        return new ResultadoCoincidenciaLinea(false, 1);
    }

    /**
     * Limpia los archivos generados en el directorio de salida.
     *
     * @return Cantidad de archivos eliminados.
     */
    public int limpiarArchivos() {
        Path dir = exportService.getDirectorioSalida();
        if (!Files.exists(dir)) {
            return 0;
        }

        int borrados = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(this::esArchivoCsvValido).toList();
            for (Path p : files) {
                Files.deleteIfExists(p);
                borrados++;
            }
        } catch (IOException e) {
            log.error("Error al limpiar archivos generados en {}", dir, e);
        }
        return borrados;
    }
}
