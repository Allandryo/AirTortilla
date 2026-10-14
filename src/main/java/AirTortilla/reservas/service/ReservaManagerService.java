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

    // Reservas cargadas actualmente en memoria
    private final List<Reserva> reservasActivas = Collections.synchronizedList(new ArrayList<>());

    public ReservaManagerService(ReservaParserService parserService, ReservaExportService exportService) {
        this.parserService = parserService;
        this.exportService = exportService;
    }

    /**
     * Carga reservas desde un archivo CSV subido por el usuario a través de la interfaz web.
     * Formato esperado de cabecera:
     * id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
     *
     * @param file Archivo subido vía multipart/form-data.
     * @return Copia defensiva de la lista de reservas cargadas.
     */
    public List<Reserva> cargarDesdeArchivoSubido(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo subido está vacío o es nulo");
        }
        try (InputStream is = file.getInputStream()) {
            List<Reserva> reservas = parserService.parsearCsv(is);
            actualizarReservasEnMemoria(reservas);
            log.info("Cargadas {} reservas desde archivo subido '{}'", reservas.size(), file.getOriginalFilename());
            return getReservasActivas();
        } catch (Exception e) {
            log.error("Error al procesar archivo subido", e);
            throw new RuntimeException("Error al procesar el archivo CSV subido: " + e.getMessage(), e);
        }
    }

    /**
     * Reemplaza de forma atómica y segura para hilos las reservas activas en memoria.
     */
    private void actualizarReservasEnMemoria(List<Reserva> nuevasReservas) {
        synchronized (reservasActivas) {
            reservasActivas.clear();
            reservasActivas.addAll(nuevasReservas);
        }
    }

    /**
     * Genera los archivos divididos por país de destino a partir de las reservas activas.
     *
     * @return Lista de metadatos de los archivos generados.
     */
    public List<ArchivoReservaInfo> generarArchivosPorDestino() {
        List<Reserva> copia = getReservasActivas();
        if (copia.isEmpty()) {
            throw new IllegalStateException("No hay reservas activas para generar archivos. Suba un CSV primero.");
        }
        return exportService.exportarArchivosPorDestinoYFecha(copia);
    }

    /**
     * Retorna una copia defensiva de las reservas activas en memoria para evitar mutaciones externas concurrentes.
     */
    public List<Reserva> getReservasActivas() {
        synchronized (reservasActivas) {
            return new ArrayList<>(reservasActivas);
        }
    }

    public List<GrupoCoincidenciaDto> obtenerCoincidenciasActivas() {
        return parserService.analizarCoincidencias(getReservasActivas());
    }
}
