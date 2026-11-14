package AirTortilla.reservas.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Manejador global centralizado de excepciones para la API REST de AirTortilla.
 * Transforma excepciones comunes en respuestas HTTP coherentes en formato JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura accesos a recursos inexistentes (ficheros no encontrados, usuarios o líneas fuera de rango)
     * y responde con código HTTP 404 Not Found.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrado(NoSuchElementException e) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Captura errores de validación de entrada o formato de datos
     * y responde con código HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarArgumentoInvalido(IllegalArgumentException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Captura violaciones de integridad o unicidad en base de datos (duplicados)
     * y responde con código HTTP 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarIntegridad(DataIntegrityViolationException e) {
        return buildErrorResponse(HttpStatus.CONFLICT,
                "No se puede guardar el registro: infringe las restricciones de unicidad en la base de datos (reserva duplicada, pasajero repetido en el mismo día o asiento ocupado).");
    }

    /**
     * Captura intentos de acceso a rutas no autorizadas o permisos insuficientes
     * y responde con código HTTP 403 Forbidden.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> manejarSeguridad(SecurityException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Acceso no autorizado";
        HttpStatus status = msg.toLowerCase().contains("iniciar sesión") ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return buildErrorResponse(status, msg);
    }

    /**
     * Captura cualquier otra excepción imprevista del servidor
     * y responde con código HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarErrorGeneral(Exception e) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor: " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String mensaje) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(error);
    }
}
