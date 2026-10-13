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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd");

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
     * Divide la lista de reservas SOLO por País de Destino (código ISO),
     * generando un fichero por cada país respetando la convención:
     * "AirTortilla_XX_YYYY_MM_DD.csv"
     * donde la fecha corresponde al momento de generación del fichero.
     */
    public List<ArchivoReservaInfo> exportarArchivosPorDestinoYFecha(List<Reserva> reservas) {
        crearDirectorioSiNoExiste();
        if (reservas == null || reservas.isEmpty()) {
            return Collections.emptyList();
        }

        // Fecha actual de generación para el nombre del fichero
        LocalDate fechaGeneracion = LocalDate.now();
        String fechaStr = fechaGeneracion.format(DATE_FORMATTER);

        // Agrupar SOLO por código ISO del país de destino
        Map<String, List<Reserva>> agrupacion = reservas.stream()
                .collect(Collectors.groupingBy(r -> {
                    String paisIso = (r.getCodigoPaisDestino() != null && !r.getCodigoPaisDestino().trim().isEmpty())
                            ? r.getCodigoPaisDestino().trim().toUpperCase()
                            : "XX";
                    return paisIso;
                }, LinkedHashMap::new, Collectors.toList()));

        List<ArchivoReservaInfo> archivosGenerados = new ArrayList<>();

        for (Map.Entry<String, List<Reserva>> entry : agrupacion.entrySet()) {
            String paisIso = entry.getKey(); // ej: "FR"
            List<Reserva> reservasGrupo = entry.getValue();

            // Nombre de fichero: AirTortilla_XX_YYYY_MM_DD.csv (fecha = fecha de generación)
            String nombreArchivo = "AirTortilla_" + paisIso + "_" + fechaStr + ".csv";

            Path archivoPath = directorioSalida.resolve(nombreArchivo);
            escribirArchivoCsv(archivoPath, reservasGrupo);

            long tamanoBytes = 0;
            try {
                tamanoBytes = Files.size(archivoPath);
            } catch (IOException ignored) {}

            ArchivoReservaInfo info = new ArchivoReservaInfo(
                    nombreArchivo,
                    paisIso,
                    fechaGeneracion,
                    reservasGrupo.size(),
                    tamanoBytes,
                    LocalDateTime.now()
            );

            archivosGenerados.add(info);
            log.info("Generado archivo {} con {} reservas", nombreArchivo, reservasGrupo.size());
        }

        return archivosGenerados;
    }

    private void escribirArchivoCsv(Path archivoPath, List<Reserva> reservas) {
        try (CSVWriter writer = new CSVWriter(
                new FileWriter(archivoPath.toFile(), StandardCharsets.UTF_8),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            // Cabecera estándar de exportación
            String[] cabecera = {
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
            writer.writeNext(cabecera);

            DateTimeFormatter isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Reserva r : reservas) {
                String[] fila = {
                        r.getId() != null ? r.getId() : "",
                        r.getNombrePasajero() != null ? r.getNombrePasajero() : "",
                        r.getOrigen() != null ? r.getOrigen() : "",
                        r.getDestino() != null ? r.getDestino() : "",
                        r.getCodigoPaisDestino() != null ? r.getCodigoPaisDestino() : "",
                        r.getFechaReserva() != null ? r.getFechaReserva().format(isoDate) : "",
                        r.getLocalizador() != null ? r.getLocalizador() : "",
                        r.getPrecio() != null ? String.valueOf(r.getPrecio()) : "",
                        r.getAsiento() != null ? r.getAsiento() : ""
                };
                writer.writeNext(fila);
            }

            writer.flush();
        } catch (IOException e) {
            log.error("Error al escribir el archivo {}", archivoPath, e);
            throw new RuntimeException("Error al exportar archivo CSV: " + archivoPath.getFileName(), e);
        }
    }
}
