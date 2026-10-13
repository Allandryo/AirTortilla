package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Servicio gestor central para coordinar el estado de reservas cargadas,
 * la generación de archivos filtrados por destino, y el análisis de colisiones.
 *
 * El sistema arranca vacío. El usuario debe subir un archivo CSV a través
 * de la interfaz web para cargar las reservas.
 */
@Service
public class ReservaManagerService {

    private static final Logger log = LoggerFactory.getLogger(ReservaManagerService.class);

    private final ReservaParserService parserService;
    private final ReservaExportService exportService;
    private final ReservaFileService fileService;

    // Reservas cargadas actualmente en memoria
    private final List<Reserva> reservasActivas = Collections.synchronizedList(new ArrayList<>());

    public ReservaManagerService(ReservaParserService parserService,
                                 ReservaExportService exportService,
                                 ReservaFileService fileService) {
        this.parserService = parserService;
        this.exportService = exportService;
        this.fileService = fileService;
    }

    /**
     * Carga reservas desde un archivo CSV subido por el usuario a través de la interfaz web.
     * Formato esperado de cabecera:
     * id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
     */
    public List<Reserva> cargarDesdeArchivoSubido(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo subido está vacío o es nulo");
        }
        try (InputStream is = file.getInputStream()) {
            List<Reserva> reservas = parserService.parsearCsv(is);
            synchronized (reservasActivas) {
                reservasActivas.clear();
                reservasActivas.addAll(reservas);
            }
            log.info("Cargadas {} reservas desde archivo subido '{}'", reservas.size(), file.getOriginalFilename());
            return new ArrayList<>(reservasActivas);
        } catch (Exception e) {
            log.error("Error al procesar archivo subido", e);
            throw new RuntimeException("Error al procesar el archivo CSV subido: " + e.getMessage(), e);
        }
    }

    /**
     * Genera los archivos divididos por país de destino a partir de las reservas activas.
     */
    public List<ArchivoReservaInfo> generarArchivosPorDestino() {
        List<Reserva> copia;
        synchronized (reservasActivas) {
            copia = new ArrayList<>(reservasActivas);
        }
        if (copia.isEmpty()) {
            throw new IllegalStateException("No hay reservas activas para generar archivos. Suba un CSV primero.");
        }
        return exportService.exportarArchivosPorDestinoYFecha(copia);
    }

    public List<Reserva> getReservasActivas() {
        synchronized (reservasActivas) {
            return new ArrayList<>(reservasActivas);
        }
    }

    public List<GrupoCoincidenciaDto> obtenerCoincidenciasActivas() {
        return parserService.analizarCoincidencias(getReservasActivas());
    }

    public ReservaFileService getFileService() {
        return fileService;
    }
}
