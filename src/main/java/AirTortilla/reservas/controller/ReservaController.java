package AirTortilla.reservas.controller;

import AirTortilla.reservas.model.*;
import AirTortilla.reservas.service.ReservaFileService;
import AirTortilla.reservas.service.ReservaManagerService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de reservas de AirTortilla y consulta
 * en modalidades Total y Línea a Línea.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaManagerService managerService;
    private final ReservaFileService fileService;

    public ReservaController(ReservaManagerService managerService, ReservaFileService fileService) {
        this.managerService = managerService;
        this.fileService = fileService;
    }

    /**
     * Obtiene el resumen y lista completa de reservas activas cargadas en el sistema.
     *
     * @return 200 OK con KPIs (total reservas, países, grupos coincidentes) y la lista de reservas.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> obtenerReservasActivas() {
        List<Reserva> reservas = managerService.getReservasActivas();
        List<GrupoCoincidenciaDto> coincidencias = managerService.obtenerCoincidenciasActivas();

        long paisesDistintos = reservas.stream()
                .map(Reserva::getCodigoPaisDestino)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .count();

        return ResponseEntity.ok(Map.of(
                "totalReservas", reservas.size(),
                "totalPaisesDestino", paisesDistintos,
                "totalGruposCoincidentes", coincidencias.size(),
                "coincidencias", coincidencias,
                "reservas", reservas
        ));
    }

    /**
     * Carga un archivo CSV subido por el usuario mediante multipart/form-data.
     * Realiza el parseo, normalización y partición automática en ficheros por país de destino.
     *
     * @param archivo Archivo MultipartFile CSV enviado desde la interfaz web.
     * @return 200 OK con resumen de registros cargados y ficheros generados.
     */
    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirCsv(@RequestParam("archivo") MultipartFile archivo) {
        List<Reserva> reservas = managerService.cargarDesdeArchivoSubido(archivo);
        List<ArchivoReservaInfo> archivos = managerService.generarArchivosPorDestino();

        return ResponseEntity.ok(Map.of(
                "mensaje", "Archivo '" + archivo.getOriginalFilename() + "' procesado correctamente",
                "totalReservas", reservas.size(),
                "totalArchivosGenerados", archivos.size(),
                "archivos", archivos
        ));
    }

    /**
     * Genera / regenera los ficheros divididos por país de destino y fecha actual.
     *
     * @return 200 OK con la lista de archivos creados.
     */
    @PostMapping("/generar-archivos")
    public ResponseEntity<Map<String, Object>> generarArchivos() {
        List<ArchivoReservaInfo> archivos = managerService.generarArchivosPorDestino();
        return ResponseEntity.ok(Map.of(
                "mensaje", "Archivos divididos por país de destino generados exitosamente",
                "totalArchivos", archivos.size(),
                "archivos", archivos
        ));
    }

    /**
     * Lista todos los archivos generados disponibles en el almacenamiento local.
     *
     * @return 200 OK con la lista de metadatos de los ficheros CSV.
     */
    @GetMapping("/archivos")
    public ResponseEntity<List<ArchivoReservaInfo>> listarArchivos() {
        return ResponseEntity.ok(fileService.listarArchivos());
    }

    /**
     * Consulta un archivo en MODALIDAD TOTAL.
     * Devuelve todas las reservas del país, el texto en crudo (raw CSV) y el reporte de colisiones.
     *
     * @param nombreArchivo Nombre del fichero CSV a consultar.
     * @return 200 OK con ArchivoDetalleDto.
     */
    @GetMapping("/archivos/{nombreArchivo}/total")
    public ResponseEntity<ArchivoDetalleDto> consultarArchivoTotal(@PathVariable String nombreArchivo) {
        ArchivoDetalleDto detalle = fileService.consultarArchivoTotal(nombreArchivo);
        return ResponseEntity.ok(detalle);
    }

    /**
     * Consulta un archivo en MODALIDAD LÍNEA A LÍNEA.
     * Permite inspeccionar registro a registro con controles de navegación paso a paso.
     *
     * @param nombreArchivo Nombre del fichero CSV.
     * @param numero Número 1-based del registro solicitado (sin incluir cabecera).
     * @return 200 OK con LineaReservaDto y datos de navegación.
     */
    @GetMapping("/archivos/{nombreArchivo}/linea")
    public ResponseEntity<LineaReservaDto> consultarArchivoLinea(
            @PathVariable String nombreArchivo,
            @RequestParam(defaultValue = "1") int numero) {
        LineaReservaDto linea = fileService.consultarArchivoLinea(nombreArchivo, numero);
        return ResponseEntity.ok(linea);
    }

    /**
     * Descarga el archivo CSV generado como adjunto seguro para el navegador.
     *
     * @param nombreArchivo Nombre del fichero CSV a descargar.
     * @return 200 OK con el recurso adjunto text/csv.
     */
    @GetMapping("/archivos/{nombreArchivo}/descargar")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable String nombreArchivo) {
        Path path = fileService.resolverArchivo(nombreArchivo);
        Resource resource = new FileSystemResource(path);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .body(resource);
    }

    /**
     * Obtiene el análisis de colisiones / pasajeros coincidentes (mismo Nombre, Origen y Destino).
     *
     * @return 200 OK con la lista de grupos coincidentes y sus localizadores diferenciados.
     */
    @GetMapping("/coincidencias")
    public ResponseEntity<List<GrupoCoincidenciaDto>> obtenerCoincidencias() {
        return ResponseEntity.ok(managerService.obtenerCoincidenciasActivas());
    }

    /**
     * Elimina todos los archivos CSV generados en el directorio de salida.
     *
     * @return 200 OK con confirmación y total de ficheros borrados.
     */
    @DeleteMapping("/archivos")
    public ResponseEntity<Map<String, Object>> limpiarArchivos() {
        int borrados = fileService.limpiarArchivos();
        return ResponseEntity.ok(Map.of(
                "mensaje", "Archivos eliminados correctamente",
                "totalBorrados", borrados
        ));
    }
}
