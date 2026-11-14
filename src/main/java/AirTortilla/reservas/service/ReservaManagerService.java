package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.Reserva;
import AirTortilla.reservas.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * ¿QUÉ HACE ESTA CLASE? (Explicación para principiantes)
 * 
 * Esta clase es el "Director de Orquesta" del sistema de reservas:
 *
 * 1. ENTRADA DE ARCHIVOS:
 *    Recibe el archivo CSV que el usuario sube desde la página web (vía MultipartFile).
 *
 * 2. COMUNICACIÓN CON LA BASE DE DATOS:
 *    Usa "ReservaRepository" para consultar las reservas que ya están guardadas
 *    en la base de datos H2 (findAll) y para guardar las nuevas (saveAll).
 *
 * 3. VALIDACIÓN:
 *    Pasa los datos a "ReservaParserService" para comprobar que no haya conflictos
 *    (por ejemplo, que el mismo pasajero no vuele dos veces el mismo día).
 *
 * 4. PARTICIONADO Y SALIDA:
 *    Pide a "ReservaExportService" que genere los archivos CSV divididos por país.
 */
@Service
public class ReservaManagerService {

    private static final Logger log = LoggerFactory.getLogger(ReservaManagerService.class);

    /**
     * Objeto que resume el resultado de cargar un archivo CSV:
     * - reservasActivas: Lista de todas las reservas que quedaron en la base de datos.
     * - nuevasAnadidas: Cuántas reservas nuevas se guardaron en la BD.
     * - idsReasignados: Cuántos IDs se tuvieron que cambiar porque ya existían.
     * - filasOmitidas: Cuántas filas se descartaron por colisión o error.
     * - detallesOmitidas: Lista de explicaciones de por qué se descartó cada fila.
     */
    public record ResultadoCargaCsv(
            List<Reserva> reservasActivas,
            int nuevasAnadidas,
            int idsReasignados,
            int filasOmitidas,
            List<String> detallesOmitidas) {
    }

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
     * Constructor de compatibilidad por si Spring inyecta opcionalmente un TransactionManager.
     */
    @Autowired
    public ReservaManagerService(ReservaParserService parserService,
                                 ReservaExportService exportService,
                                 ReservaRepository reservaRepository,
                                 @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this(parserService, exportService, reservaRepository);
    }

    /**
     * Versión simplificada para obtener directamente la lista de reservas tras subir un archivo.
     */
    public List<Reserva> cargarDesdeArchivoSubido(MultipartFile file) {
        return cargarDesdeArchivoSubidoConResumen(file).reservasActivas();
    }

    /**
     * PROCESO PRINCIPAL DE ENTRADA DE ARCHIVO Y GUARDADO EN BASE DE DATOS:
     *
     * @Transactional asegura que si ocurre algún fallo inesperado, no queden datos
     * a medias en la base de datos (hace un Rollback automático).
     * synchronized evita que dos usuarios suban archivos exactamente al mismo milisegundo.
     *
     * @param file Archivo CSV subido por el usuario.
     * @return Resumen con las reservas guardadas y las estadísticas.
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized ResultadoCargaCsv cargarDesdeArchivoSubidoConResumen(MultipartFile file) {

        // -------------------------------------------------------------
        // PASO 1: Validar que el archivo subido exista y no esté vacío
        // -------------------------------------------------------------
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo subido está vacío o es nulo");
        }

        try (InputStream inputStream = file.getInputStream()) {

            // -------------------------------------------------------------
            // PASO 2: Leer de la Base de Datos las reservas que ya tenemos
            // -------------------------------------------------------------
            // Usamos reservaRepository.findAll(), que ejecuta: SELECT * FROM reservas;
            List<Reserva> existentesEnBd = reservaRepository.findAll();

            // -------------------------------------------------------------
            // PASO 3: Parsear el archivo CSV y filtrar conflictos
            // -------------------------------------------------------------
            // Compara cada fila del CSV contra las reservas que ya están en la BD:
            // - Descarta duplicados exactos.
            // - Descarta vuelos del mismo pasajero en el mismo día.
            // - Descarta asientos repetidos en el mismo vuelo y fecha.
            // - Descarta localizadores ya existentes.
            ReservaParserService.ResultadoParseo resultado = parserService.parsearConResumen(inputStream, existentesEnBd);
            List<Reserva> reservasValidas = resultado.reservas();

            // -------------------------------------------------------------
            // PASO 4: Si ninguna fila fue válida, avisar con un error claro
            // -------------------------------------------------------------
            if (reservasValidas.isEmpty()) {
                if (resultado.filasOmitidas() > 0) {
                    throw new IllegalArgumentException(String.format(
                            "No se añadió ninguna reserva nueva: se omitieron todas las filas (%d) por coincidencia o conflicto. %s",
                            resultado.filasOmitidas(),
                            resultado.detallesOmitidas().getFirst()));
                }
                throw new IllegalArgumentException("El archivo CSV no contiene filas de reservas válidas.");
            }

            // -------------------------------------------------------------
            // PASO 5: Guardar en la Base de Datos (H2) las reservas válidas
            // -------------------------------------------------------------
            // reservaRepository.saveAll(reservas) hace un INSERT de cada reserva nueva.
            reservaRepository.saveAll(reservasValidas);

            log.info("Añadidas {} nuevas reservas en base de datos H2 desde '{}' ({} omitidas, {} IDs reasignados)",
                    reservasValidas.size(), file.getOriginalFilename(), resultado.filasOmitidas(), resultado.idsReasignados());

            // -------------------------------------------------------------
            // PASO 6: Devolver el resumen con todas las reservas activas
            // -------------------------------------------------------------
            List<Reserva> todasLasActivas = reservaRepository.findAll();
            return new ResultadoCargaCsv(
                    todasLasActivas,
                    reservasValidas.size(),
                    resultado.idsReasignados(),
                    resultado.filasOmitidas(),
                    resultado.detallesOmitidas()
            );

        } catch (IllegalArgumentException e) {
            log.warn("Carga rechazada sin filas válidas: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error al procesar archivo subido", e);
            throw new RuntimeException("Error al procesar el archivo CSV subido: " + e.getMessage(), e);
        }
    }

    /**
     * Guarda una lista de reservas en la base de datos relacional H2.
     *
     * @param nuevasReservas Colección de reservas validadas a guardar.
     */
    @Transactional
    public void agregarReservasEnBaseDeDatos(List<Reserva> nuevasReservas) {
        if (nuevasReservas != null && !nuevasReservas.isEmpty()) {
            reservaRepository.saveAll(nuevasReservas);
        }
    }

    /**
     * Borra todas las reservas de la base de datos (SELECT * FROM reservas -> DELETE).
     */
    @Transactional
    public void vaciarReservasEnBaseDeDatos() {
        reservaRepository.deleteAll();
    }

    /**
     * Genera los archivos CSV divididos por país de destino a partir de las reservas
     * que están actualmente en la base de datos.
     *
     * @return Lista con los metadatos de los archivos generados.
     */
    public List<ArchivoReservaInfo> generarArchivosPorDestino() {
        List<Reserva> activas = getReservasActivas();
        if (activas.isEmpty()) {
            throw new IllegalStateException("No hay reservas activas para generar archivos. Suba un CSV primero.");
        }
        return exportService.exportarArchivosPorDestinoYFecha(activas);
    }

    /**
     * Consulta todas las reservas guardadas en la base de datos H2.
     * Equivale a ejecutar en SQL: SELECT * FROM reservas;
     */
    public List<Reserva> getReservasActivas() {
        return reservaRepository.findAll();
    }

    /**
     * Consulta reservas filtrando por país de destino directamente en la BD.
     * Equivale a ejecutar en SQL: SELECT * FROM reservas WHERE pais_destino = 'ES';
     *
     * @param codigoPais Código ISO del país (ej: "ES", "IT").
     */
    public List<Reserva> getReservasPorPais(String codigoPais) {
        if (codigoPais == null || codigoPais.isBlank()) {
            return getReservasActivas();
        }
        return reservaRepository.findByCodigoPaisDestino(codigoPais.trim().toUpperCase());
    }

    /**
     * Analiza las reservas activas en la BD para encontrar pasajeros que tengan
     * el mismo Nombre, Origen y Destino (grupos de coincidencia).
     */
    public List<GrupoCoincidenciaDto> obtenerCoincidenciasActivas() {
        return parserService.analizarCoincidencias(getReservasActivas());
    }
}
