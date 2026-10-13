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
     * Obtiene el resumen y lista de reservas activas cargadas en el sistema.
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

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("totalReservas", reservas.size());
        respuesta.put("totalPaisesDestino", paisesDistintos);
        respuesta.put("totalGruposCoincidentes", coincidencias.size());
        respuesta.put("coincidencias", coincidencias);
        respuesta.put("reservas", reservas);

        return ResponseEntity.ok(respuesta);
    }



    /**
     * Carga un archivo CSV subido por el usuario a través de la interfaz web.
     */
    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirCsv(@RequestParam("archivo") MultipartFile archivo) {
        List<Reserva> reservas = managerService.cargarDesdeArchivoSubido(archivo);
        List<ArchivoReservaInfo> archivos = managerService.generarArchivosPorDestino();

        Map<String, Object> resp = new HashMap<>();
        resp.put("mensaje", "Archivo '" + archivo.getOriginalFilename() + "' procesado correctamente");
        resp.put("totalReservas", reservas.size());
        resp.put("totalArchivosGenerados", archivos.size());
        resp.put("archivos", archivos);

        return ResponseEntity.ok(resp);
    }

    /**
     * Genera / regenera los ficheros divididos por país de destino y fecha.
     */
    @PostMapping("/generar-archivos")
    public ResponseEntity<Map<String, Object>> generarArchivos() {
        List<ArchivoReservaInfo> archivos = managerService.generarArchivosPorDestino();
        Map<String, Object> resp = new HashMap<>();
        resp.put("mensaje", "Archivos divididos por país de destino generados exitosamente");
        resp.put("totalArchivos", archivos.size());
        resp.put("archivos", archivos);
        return ResponseEntity.ok(resp);
    }

    /**
     * Lista los archivos generados con sus metadatos.
     */
    @GetMapping("/archivos")
    public ResponseEntity<List<ArchivoReservaInfo>> listarArchivos() {
        return ResponseEntity.ok(fileService.listarArchivos());
    }

    /**
     * Consulta un archivo en MODALIDAD TOTAL.
     */
    @GetMapping("/archivos/{nombreArchivo}/total")
    public ResponseEntity<ArchivoDetalleDto> consultarArchivoTotal(@PathVariable String nombreArchivo) {
        ArchivoDetalleDto detalle = fileService.consultarArchivoTotal(nombreArchivo);
        return ResponseEntity.ok(detalle);
    }

    /**
     * Consulta un archivo en MODALIDAD LÍNEA A LÍNEA.
     */
    @GetMapping("/archivos/{nombreArchivo}/linea")
    public ResponseEntity<LineaReservaDto> consultarArchivoLinea(
            @PathVariable String nombreArchivo,
            @RequestParam(defaultValue = "1") int numero) {
        LineaReservaDto linea = fileService.consultarArchivoLinea(nombreArchivo, numero);
        return ResponseEntity.ok(linea);
    }

    /**
     * Descarga el archivo CSV generado.
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
     * Obtiene el análisis de coincidencias de pasajeros (mismo nombre, origen y destino).
     */
    @GetMapping("/coincidencias")
    public ResponseEntity<List<GrupoCoincidenciaDto>> obtenerCoincidencias() {
        return ResponseEntity.ok(managerService.obtenerCoincidenciasActivas());
    }

    /**
     * Limpia los archivos generados.
     */
    @DeleteMapping("/archivos")
    public ResponseEntity<Map<String, Object>> limpiarArchivos() {
        int borrados = fileService.limpiarArchivos();
        Map<String, Object> resp = new HashMap<>();
        resp.put("mensaje", "Archivos eliminados correctamente");
        resp.put("totalBorrados", borrados);
        return ResponseEntity.ok(resp);
    }
}
