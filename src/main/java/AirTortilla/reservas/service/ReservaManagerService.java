package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.Reserva;
import AirTortilla.reservas.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Servicio gestor central para coordinar el estado de reservas,
 * la persistencia relacional con Spring Data JPA en H2,
 * la generación de archivos filtrados por destino, y el análisis de colisiones.
 *
 * El usuario puede subir un archivo CSV a través de la interfaz web para persistir
 * y actualizar las reservas en la base de datos relacional.
 */
@Service
public class ReservaManagerService {

    private static final Logger log = LoggerFactory.getLogger(ReservaManagerService.class);

    private final ReservaParserService parserService;
    private final ReservaExportService exportService;
    private final ReservaRepository reservaRepository;

    public ReservaManagerService(ReservaParserService parserService,
                                 ReservaExportService exportService,
                                 ReservaRepository reservaRepository) {
        this.parserService = parserService;
        this.exportService = exportService;
        this.reservaRepository = reservaRepository;
    }

    /**
     * Carga reservas desde un archivo CSV subido por el usuario a través de la interfaz web
     * y las persiste de forma transaccional en la base de datos relacional H2.
     *
     * @param file Archivo subido vía multipart/form-data.
     * @return Lista de reservas guardadas y activas.
     */
    @Transactional
    public List<Reserva> cargarDesdeArchivoSubido(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo subido está vacío o es nulo");
        }
        try (InputStream is = file.getInputStream()) {
            List<Reserva> reservas = parserService.parsearCsv(is);
            actualizarReservasEnBaseDeDatos(reservas);
            log.info("Cargadas {} reservas en base de datos H2 desde archivo subido '{}'", reservas.size(), file.getOriginalFilename());
            return getReservasActivas();
        } catch (Exception e) {
            log.error("Error al procesar archivo subido", e);
            throw new RuntimeException("Error al procesar el archivo CSV subido: " + e.getMessage(), e);
        }
    }

    /**
     * Reemplaza de forma transaccional las reservas en la base de datos relacional.
     *
     * @param nuevasReservas Colección de reservas normalizadas a persistir.
     */
    @Transactional
    public void actualizarReservasEnBaseDeDatos(List<Reserva> nuevasReservas) {
        reservaRepository.deleteAll();
        if (nuevasReservas != null && !nuevasReservas.isEmpty()) {
            reservaRepository.saveAll(nuevasReservas);
        }
    }

    /**
     * Genera los archivos divididos por país de destino a partir de las reservas activas en la BD.
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
     * Obtiene la lista completa de reservas activas almacenadas en la base de datos relacional H2.
     */
    public List<Reserva> getReservasActivas() {
        return reservaRepository.findAll();
    }

    public List<GrupoCoincidenciaDto> obtenerCoincidenciasActivas() {
        return parserService.analizarCoincidencias(getReservasActivas());
    }
}
