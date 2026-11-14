package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoDetalleDto;
import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.LineaReservaDto;
import AirTortilla.reservas.model.Reserva;
import AirTortilla.reservas.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
 * ¿QUÉ HACE ESTA CLASE? (Explicación para principiantes)
 * 
 * Este servicio gestiona las consultas de los archivos CSV generados por país:
 *
 * 1. LISTAR ARCHIVOS:
 * Permite a la interfaz web saber qué archivos existen (ej:
 * AirTortilla_ES_2026_11_11.csv).
 *
 * 2. MODALIDAD TOTAL:
 * Devuelve todas las reservas de un país en un solo bloque, leyendo
 * preferentemente
 * de la Base de Datos H2 (rápido y actualizado), o desde el archivo físico si
 * no hay BD.
 *
 * 3. MODALIDAD LÍNEA A LÍNEA:
 * Permite al usuario examinar una fila concreta (por ejemplo, "Fila 3 de 10") y
 * navegar con botones "Anterior" y "Siguiente".
 *
 * 4. SEGURIDAD:
 * Valida que los nombres de archivo no contengan caracteres peligrosos como
 * ".." o "/"
 * para evitar ataques de acceso a archivos no autorizados (Path Traversal).
 */
@Service
public class ReservaFileService {

    private static final Logger log = LoggerFactory.getLogger(ReservaFileService.class);

    // Expresión regular para extraer el país y la fecha del nombre del archivo:
    // Ejemplo: "AirTortilla_IT_2026_11_11.csv" -> Grupo 1: "IT", Grupo 2..4:
    // "2026", "11", "11"
    private static final Pattern FILENAME_PATTERN = Pattern
            .compile("^AirTortilla_([A-Za-z0-9]+)(?:_(\\d{4})_(\\d{2})_(\\d{2}))?\\.csv$");

    private final ReservaExportService exportService;
    private final ReservaParserService parserService;
    private final ReservaRepository reservaRepository;

    /**
     * Información básica extraída del nombre de un archivo CSV.
     */
    public record InfoNombreArchivo(String pais, LocalDate fecha) {
    }

    @Autowired
    public ReservaFileService(ReservaExportService exportService,
            ReservaParserService parserService,
            @Autowired(required = false) ReservaRepository reservaRepository) {
        this.exportService = exportService;
        this.parserService = parserService;
        this.reservaRepository = reservaRepository;
    }

    public ReservaFileService(ReservaExportService exportService, ReservaParserService parserService) {
        this(exportService, parserService, null);
    }

    // SECCIÓN 1
    /**
     * Valida y obtiene la ruta física en disco de un archivo CSV en la carpeta
     * autorizada.
     * Si no existe en disco pero sí hay reservas en la BD, lo genera al vuelo.
     */
    public Path resolverArchivo(String nombreArchivo) {
        // Validación de seguridad para evitar salir de la carpeta autorizada
        if (nombreArchivo == null || nombreArchivo.contains("..") || nombreArchivo.contains("/")
                || nombreArchivo.contains("\\")) {
            throw new IllegalArgumentException("Nombre de archivo inválido o peligroso: " + nombreArchivo);
        }

        Path path = exportService.getDirectorioSalida().resolve(nombreArchivo).normalize();
        if (!path.startsWith(exportService.getDirectorioSalida())) {
            throw new SecurityException("Acceso denegado a ruta no autorizada");
        }

        // Si el archivo físico no existe en disco, intentamos regenerarlo desde la BD
        if (!Files.exists(path)) {
            if (reservaRepository != null) {
                InfoNombreArchivo info = parsearInfoDesdeNombre(nombreArchivo);
                List<Reserva> reservas = reservaRepository.findByCodigoPaisDestino(info.pais());
                if (!reservas.isEmpty()) {
                    exportService.exportarArchivosPorDestinoYFecha(reservas);
                    if (Files.exists(path)) {
                        return path;
                    }
                }
            }
            throw new NoSuchElementException("El archivo no existe: " + nombreArchivo);
        }

        return path;
    }

    /**
     * Lista todos los archivos CSV disponibles en la carpeta de salida.
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
        return nombre.startsWith(ReservaExportService.PREFIJO_ARCHIVO)
                && nombre.endsWith(ReservaExportService.EXTENSION_CSV);
    }

    private Optional<ArchivoReservaInfo> construirInfoArchivo(Path path) {
        try {
            String nombre = path.getFileName().toString();
            InfoNombreArchivo metaNombre = parsearInfoDesdeNombre(nombre);

            long tamano = Files.size(path);
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            LocalDateTime fechaGen = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(),
                    ZoneId.systemDefault());

            int totalReservas;
            if (reservaRepository != null) {
                List<Reserva> enBd = reservaRepository.findByCodigoPaisDestino(metaNombre.pais());
                totalReservas = !enBd.isEmpty() ? enBd.size() : contarLineasDeDatos(path);
            } else {
                totalReservas = contarLineasDeDatos(path);
            }

            return Optional.of(new ArchivoReservaInfo(
                    nombre,
                    metaNombre.pais(),
                    metaNombre.fecha(),
                    totalReservas,
                    tamano,
                    fechaGen));
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
     * Extrae el código de país y la fecha a partir del nombre del archivo:
     * "AirTortilla_IT_2026_11_11.csv" -> País "IT", Fecha 2026-11-11
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

    // SECCIÓN 2
    /**
     * Consulta todas las reservas de un país (Modalidad Total).
     * Lee directamente desde la Base de Datos H2 para máxima velocidad y
     * coherencia.
     */
    public ArchivoDetalleDto consultarArchivoTotal(String nombreArchivo) {
        InfoNombreArchivo metaNombre = parsearInfoDesdeNombre(nombreArchivo);
        String paisIso = metaNombre.pais();

        // 1. Lectura prioritaria desde la BASE DE DATOS H2
        if (reservaRepository != null) {
            List<Reserva> reservasBd = reservaRepository.findByCodigoPaisDestino(paisIso);
            if (!reservasBd.isEmpty()) {
                log.info("Consultando archivo total '{}' directamente desde la BD H2 ({} reservas)", nombreArchivo,
                        reservasBd.size());

                List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservasBd);
                String contenidoRaw = exportService.generarCsvTexto(reservasBd);

                Path path = exportService.getDirectorioSalida().resolve(nombreArchivo).normalize();
                ArchivoReservaInfo info;
                if (Files.exists(path)) {
                    info = construirInfoArchivo(path)
                            .orElseGet(() -> new ArchivoReservaInfo(
                                    nombreArchivo,
                                    paisIso,
                                    metaNombre.fecha(),
                                    reservasBd.size(),
                                    contenidoRaw.getBytes(StandardCharsets.UTF_8).length,
                                    LocalDateTime.now()));
                } else {
                    info = new ArchivoReservaInfo(
                            nombreArchivo,
                            paisIso,
                            metaNombre.fecha(),
                            reservasBd.size(),
                            contenidoRaw.getBytes(StandardCharsets.UTF_8).length,
                            LocalDateTime.now());
                }

                return new ArchivoDetalleDto(info, reservasBd, contenidoRaw, grupos);
            }
        }

        // 2. Fallback: Si no hay repositorio o la BD está vacía, leer del archivo
        // físico en disco
        Path path = resolverArchivo(nombreArchivo);
        try {
            String contenidoRaw = Files.readString(path, StandardCharsets.UTF_8);
            List<Reserva> reservas = parserService.parsearCsvTexto(contenidoRaw);
            List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservas);

            ArchivoReservaInfo info = construirInfoArchivo(path)
                    .orElseThrow(
                            () -> new NoSuchElementException("No se pudo obtener información de " + nombreArchivo));

            return new ArchivoDetalleDto(info, reservas, contenidoRaw, grupos);
        } catch (IOException e) {
            log.error("Error al leer archivo total: {}", nombreArchivo, e);
            throw new RuntimeException("Error al leer el archivo en modalidad total: " + e.getMessage(), e);
        }
    }

    // SECCIÓN 3
    /**
     * Consulta una fila específica de un archivo (Modalidad Línea a Línea).
     * Permite examinar la reserva registro a registro.
     *
     * @param nombreArchivo Nombre del fichero CSV.
     * @param numeroLinea   Número de fila solicitada (1 para la primera fila de
     *                      datos).
     */
    public LineaReservaDto consultarArchivoLinea(String nombreArchivo, int numeroLinea) {
        InfoNombreArchivo metaNombre = parsearInfoDesdeNombre(nombreArchivo);
        String paisIso = metaNombre.pais();

        // 1. Lectura prioritaria desde la BASE DE DATOS H2
        if (reservaRepository != null) {
            List<Reserva> reservasBd = reservaRepository.findByCodigoPaisDestino(paisIso);
            if (!reservasBd.isEmpty()) {
                int totalLineas = reservasBd.size();
                int lineaAjustada = Math.max(1, Math.min(numeroLinea, totalLineas));
                Reserva reserva = reservasBd.get(lineaAjustada - 1);

                log.info("Consultando línea {}/{} de '{}' directamente desde la BD H2", lineaAjustada, totalLineas,
                        nombreArchivo);

                String lineaRaw = exportService.convertirReservaALineaCsvTexto(reserva);

                // Comprobar si esta reserva forma parte de un grupo de coincidencia
                List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservasBd);
                String clave = reserva.getClaveCoincidencia();
                boolean esCoincidente = false;
                int totalCoincidentes = 1;

                for (GrupoCoincidenciaDto g : grupos) {
                    if (g.getClave().equals(clave)) {
                        esCoincidente = true;
                        totalCoincidentes = g.getTotalCoincidentes();
                        break;
                    }
                }

                return new LineaReservaDto(
                        nombreArchivo,
                        lineaAjustada,
                        totalLineas,
                        lineaRaw,
                        reserva,
                        lineaAjustada > 1, // tieneAnterior
                        lineaAjustada < totalLineas, // tieneSiguiente
                        esCoincidente,
                        totalCoincidentes);
            }
        }

        // 2. Fallback: Leer desde el archivo físico en disco
        Path path = resolverArchivo(nombreArchivo);
        List<String> todasLasLineas = leerLineasSeguro(path);

        String cabecera = todasLasLineas.getFirst();
        List<String> lineasDatos = todasLasLineas.subList(1, todasLasLineas.size());
        int totalLineas = lineasDatos.size();

        if (totalLineas == 0) {
            throw new NoSuchElementException("El archivo no contiene filas de datos");
        }

        int lineaAjustada = Math.max(1, Math.min(numeroLinea, totalLineas));
        String lineaRaw = lineasDatos.get(lineaAjustada - 1);

        Reserva reserva = parsearFilaIndividual(cabecera, lineaRaw);
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
                colision.totalCoincidentes());
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

    private record ResultadoCoincidenciaLinea(boolean esCoincidente, int totalCoincidentes) {
    }

    private ResultadoCoincidenciaLinea verificarCoincidenciaFila(Reserva reserva, List<String> todasLasLineas) {
        if (reserva == null) {
            return new ResultadoCoincidenciaLinea(false, 1);
        }

        List<Reserva> todasReservas = parserService
                .parsearCsvTexto(String.join(System.lineSeparator(), todasLasLineas));
        List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(todasReservas);

        String clave = reserva.getClaveCoincidencia();
        for (GrupoCoincidenciaDto g : grupos) {
            if (g.getClave().equals(clave)) {
                return new ResultadoCoincidenciaLinea(true, g.getTotalCoincidentes());
            }
        }
        return new ResultadoCoincidenciaLinea(false, 1);
    }

    // SECCIÓN 4
    /**
     * Elimina todos los archivos CSV generados en la carpeta de salida.
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
