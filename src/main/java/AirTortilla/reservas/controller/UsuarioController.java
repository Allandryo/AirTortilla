package AirTortilla.reservas.controller;

import AirTortilla.reservas.model.Usuario;
import AirTortilla.reservas.model.UsuarioDto;
import AirTortilla.reservas.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el mantenimiento y administración de usuarios en la tabla 'usuarios'.
 * Operaciones CRUD exclusivas para usuarios con rol 'ADMIN'.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Valida que la petición provenga de un usuario autenticado con permisos de Administrador.
     * Retorna el usuario de la sesión o lanza SecurityException / IllegalArgumentException.
     */
    private Usuario validarAdministrador(HttpServletRequest request) {
        return AuthController.requerirAdministrador(request, "gestionar usuarios");
    }

    /**
     * Lista todos los usuarios registrados en la tabla 'usuarios'.
     * Exclusivo para administradores.
     */
    @GetMapping
    public ResponseEntity<List<UsuarioDto>> listarUsuarios(HttpServletRequest request) {
        validarAdministrador(request);
        return ResponseEntity.ok(usuarioService.listarUsuarios());
    }

    /**
     * Obtiene el detalle de un usuario por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDto> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        validarAdministrador(request);
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    /**
     * Crea un nuevo usuario en la base de datos (con rol ADMIN o NORMAL).
     * Exclusivo para administradores.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crearUsuario(@RequestBody Usuario nuevoUsuario, HttpServletRequest request) {
        validarAdministrador(request);
        UsuarioDto creado = usuarioService.crearUsuario(nuevoUsuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Usuario '" + creado.getUsername() + "' creado exitosamente.",
                "usuario", creado
        ));
    }

    /**
     * Actualiza los datos de un usuario existente (nombre, username, rol y opcionalmente contraseña).
     * Exclusivo para administradores.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody Usuario datosActualizados,
            HttpServletRequest request) {
        Usuario adminSesion = validarAdministrador(request);
        UsuarioDto actualizado = usuarioService.actualizarUsuario(id, datosActualizados);

        // Si el admin editó su propio usuario, actualizar los datos guardados en la sesión
        if (id.equals(adminSesion.getId())) {
            adminSesion.setNombre(actualizado.getNombre());
            adminSesion.setUsername(actualizado.getUsername());
            adminSesion.setRol(actualizado.getRol());
            request.getSession().setAttribute(AuthController.SESSION_USER_KEY, adminSesion);
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Usuario actualizado exitosamente.",
                "usuario", actualizado
        ));
    }

    /**
     * Elimina un usuario por su ID.
     * Exclusivo para administradores (no permite autoeliminación ni borrar al último admin).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarUsuario(@PathVariable Long id, HttpServletRequest request) {
        Usuario adminSesion = validarAdministrador(request);
        usuarioService.eliminarUsuario(id, adminSesion.getId());
        return ResponseEntity.ok(Map.of(
                "mensaje", "Usuario con ID " + id + " eliminado correctamente."
        ));
    }
}
