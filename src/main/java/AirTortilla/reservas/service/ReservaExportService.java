package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.Reserva;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio encargado de generar y exportar ficheros de reservas divididos
 * exclusivamente por código ISO del país de destino.
 *
 * Formato de nombre exigido: "AirTortilla_XX_YYYY_MM_DD"
 * donde:
 *   XX   = Código ISO de 2 caracteres del país de destino (en mayúsculas)
 *   YYYY = Año de generación del fichero
 *   MM   = Mes de generación (2 dígitos)
 *   DD   = Día de generación (2 dígitos)
 *
 * Se genera UN fichero por país de destino que contiene TODAS las reservas
 * con ese destino, independientemente de su fecha de reserva individual.
 */
@Service
public class ReservaExportService {

    private static final Logger log = LoggerFactory.getLogger(ReservaExportService.class);

    public static final String PREFIJO_ARCHIVO = "AirTortilla_";
    public static final String EXTENSION_CSV = ".csv";
    public static final String PATRON_FECHA_ARCHIVO = "yyyy_MM_dd";
    public static final String PATRON_FECHA_RESERVA = "yyyy-MM-dd";

    /**
     * Columnas estandarizadas del fichero CSV de reservas.
     */
    public static final String[] CABECERA_CSV = {
            "id",
            "nombre_pasajero",
            "origen",
            "destino",
            "pais_destino",
            "fecha_reserva",
            "localizador",
            "precio",
            "asiento"
    };

    private static final DateTimeFormatter FORMATTER_NOMBRE_ARCHIVO = DateTimeFormatter.ofPattern(PATRON_FECHA_ARCHIVO);
    private static final DateTimeFormatter FORMATTER_FECHA_RESERVA = DateTimeFormatter.ofPattern(PATRON_FECHA_RESERVA);

    private final Path directorioSalida;

    public ReservaExportService(@Value("${airtortilla.output-dir:archivos_generados}") String outputDir) {
        this.directorioSalida = Paths.get(outputDir).toAbsolutePath().normalize();
        crearDirectorioSiNoExiste();
    }

    private void crearDirectorioSiNoExiste() {
        try {
            if (!Files.exists(directorioSalida)) {
                Files.createDirectories(directorioSalida);
                log.info("Directorio de salida creado en: {}", directorioSalida);
            }
        } catch (IOException e) {
            log.error("No se pudo crear el directorio de salida: {}", directorioSalida, e);
        }
    }

    public Path getDirectorioSalida() {
        return directorioSalida;
    }

    /**
     * Divide la lista de reservas ÚNICAMENTE por País de Destino (código ISO de 2 letras),
     * generando un fichero por cada país con la convención:
     * "AirTortilla_XX_YYYY_MM_DD.csv"
     * donde la fecha corresponde al momento de generación del fichero.
     *
     * Regla de negocio: Todas las reservas hacia el mismo país van juntas en el mismo archivo,
     * sin importar la ciudad concreta ni la fecha individual del vuelo.
     *
     * @param reservas Colección de reservas a particionar y exportar.
     * @return Lista de metadatos de los archivos CSV generados.
     */
    public List<ArchivoReservaInfo> exportarArchivosPorDestinoYFecha(List<Reserva> reservas) {
        crearDirectorioSiNoExiste();
        if (reservas == null || reservas.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate fechaGeneracion = LocalDate.now();
        String fechaStr = fechaGeneracion.format(FORMATTER_NOMBRE_ARCHIVO);

        // Agrupación exclusiva por código ISO del país de destino
        Map<String, List<Reserva>> reservasPorPais = agruparPorPaisDestino(reservas);

        List<ArchivoReservaInfo> archivosGenerados = new ArrayList<>();
        for (Map.Entry<String, List<Reserva>> entry : reservasPorPais.entrySet()) {
            String paisIso = entry.getKey();
            List<Reserva> reservasPais = entry.getValue();

            ArchivoReservaInfo info = exportarArchivoDePais(paisIso, reservasPais, fechaGeneracion, fechaStr);
            archivosGenerados.add(info);
        }

        return archivosGenerados;
    }

    /**
     * Genera y escribe el archivo CSV para un país concreto y devuelve sus metadatos.
     */
    private ArchivoReservaInfo exportarArchivoDePais(String paisIso, List<Reserva> reservasPais,
                                                     LocalDate fechaGeneracion, String fechaStr) {
        String nombreArchivo = construirNombreArchivo(paisIso, fechaStr);
        Path archivoPath = directorioSalida.resolve(nombreArchivo);

        escribirArchivoCsv(archivoPath, reservasPais);

        long tamanoBytes = obtenerTamanoArchivo(archivoPath);
        log.info("Generado archivo {} con {} reservas", nombreArchivo, reservasPais.size());

        return new ArchivoReservaInfo(
                nombreArchivo,
                paisIso,
                fechaGeneracion,
                reservasPais.size(),
                tamanoBytes,
                LocalDateTime.now()
        );
    }

    /**
     * Agrupa las reservas por código ISO de país en mayúsculas (ej: 'ES', 'FR').
     */
    private Map<String, List<Reserva>> agruparPorPaisDestino(List<Reserva> reservas) {
        return reservas.stream()
                .collect(Collectors.groupingBy(this::obtenerCodigoPaisNormalizado, LinkedHashMap::new, Collectors.toList()));
    }

    private String obtenerCodigoPaisNormalizado(Reserva r) {
        String pais = r.getCodigoPaisDestino();
        return (pais != null && !pais.isBlank())
                ? pais.trim().toUpperCase()
                : ReservaParserService.PAIS_DESCONOCIDO;
    }

    /**
     * Construye el nombre estandarizado del fichero CSV: AirTortilla_XX_YYYY_MM_DD.csv
     */
    public static String construirNombreArchivo(String paisIso, String fechaStr) {
        return PREFIJO_ARCHIVO + paisIso + "_" + fechaStr + EXTENSION_CSV;
    }

    private long obtenerTamanoArchivo(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.warn("No se pudo obtener el tamaño de {}", path, e);
            return 0;
        }
    }

    /**
     * Escribe las filas de reservas en disco con formato CSV estándar UTF-8.
     */
    private void escribirArchivoCsv(Path archivoPath, List<Reserva> reservas) {
        try (CSVWriter writer = new CSVWriter(
                new FileWriter(archivoPath.toFile(), StandardCharsets.UTF_8),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            // 1. Escribir cabecera estándar
            writer.writeNext(CABECERA_CSV);

            // 2. Escribir cada una de las filas
            for (Reserva r : reservas) {
                writer.writeNext(convertirReservaAFilaCsv(r));
            }

            writer.flush();
        } catch (IOException e) {
            log.error("Error al escribir el archivo {}", archivoPath, e);
            throw new RuntimeException("Error al exportar archivo CSV: " + archivoPath.getFileName(), e);
        }
    }

    /**
     * Transforma una entidad Reserva en un array de cadenas conforme a CABECERA_CSV.
     * Utiliza Objects.toString para evitar comprobaciones manuales repetitivas de nulidad.
     */
    private String[] convertirReservaAFilaCsv(Reserva r) {
        String fechaStr = (r.getFechaReserva() != null)
                ? r.getFechaReserva().format(FORMATTER_FECHA_RESERVA)
                : "";

        return new String[] {
                Objects.toString(r.getId(), ""),
                Objects.toString(r.getNombrePasajero(), ""),
                Objects.toString(r.getOrigen(), ""),
                Objects.toString(r.getDestino(), ""),
                Objects.toString(r.getCodigoPaisDestino(), ""),
                fechaStr,
                Objects.toString(r.getLocalizador(), ""),
                Objects.toString(r.getPrecio(), ""),
                Objects.toString(r.getAsiento(), "")
        };
    }
}
