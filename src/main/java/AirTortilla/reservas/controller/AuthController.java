package AirTortilla.reservas.controller;

import AirTortilla.reservas.model.LoginRequest;
import AirTortilla.reservas.model.Usuario;
import AirTortilla.reservas.model.UsuarioDto;
import AirTortilla.reservas.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST encargado del flujo de autenticación, comprobación de registro en la BD 'usuarios',
 * control de sesión y cierre de sesión.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER_KEY = "USUARIO_ACTIVO";

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Autentica al usuario consultando la tabla 'usuarios' en la base de datos H2.
     * Si las credenciales coinciden, almacena el usuario en la sesión HTTP.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest == null ||
                loginRequest.getUsername() == null || loginRequest.getUsername().isBlank() ||
                loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "mensaje", "Debe ingresar tanto el nombre de usuario como la contraseña."
            ));
        }

        Optional<Usuario> usuarioOpt = usuarioService.autenticar(
                loginRequest.getUsername(),
                loginRequest.getPassword()
        );

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "mensaje", "Credenciales incorrectas o usuario no registrado en la base de datos."
            ));
        }

        Usuario usuario = usuarioOpt.get();
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_KEY, usuario);

        UsuarioDto dto = UsuarioDto.fromEntity(usuario);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Inicio de sesión exitoso. Bienvenido/a, " + usuario.getNombre(),
                "usuario", dto
        ));
    }

    /**
     * Devuelve el usuario activo actualmente en la sesión HTTP.
     * Permite al frontend verificar si ya existe una sesión iniciada al cargar la página.
     */
    @GetMapping("/session")
    public ResponseEntity<?> obtenerSesionActiva(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "mensaje", "No hay ninguna sesión activa iniciada."
            ));
        }

        Usuario usuario = (Usuario) session.getAttribute(SESSION_USER_KEY);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "mensaje", "Sesión no válida o expirada."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "autenticado", true,
                "usuario", UsuarioDto.fromEntity(usuario)
        ));
    }

    /**
     * Valida que la petición HTTP pertenezca a un usuario autenticado con rol {@code ADMIN}.
     * Lanza {@link SecurityException} si no hay sesión activa (401) o si el rol no es administrador (403).
     *
     * @param request Petición HTTP con la sesión del usuario.
     * @param accionDenegada Descripción de la acción restringida (ej. "vaciar la base de datos").
     * @return Usuario administrador autenticado en la sesión.
     */
    public static Usuario requerirAdministrador(HttpServletRequest request, String accionDenegada) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SecurityException("Debe iniciar sesión para realizar esta acción.");
        }
        Usuario usuario = (Usuario) session.getAttribute(SESSION_USER_KEY);
        if (usuario == null) {
            throw new SecurityException("Debe iniciar sesión para realizar esta acción.");
        }
        if (!usuario.esAdmin()) {
            throw new SecurityException("Acceso denegado: Solo los usuarios con rol 'ADMIN' pueden " + accionDenegada + ".");
        }
        return usuario;
    }

    /**
     * Cierra la sesión activa del usuario invalidando la sesión HTTP.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "Sesión cerrada correctamente."
        ));
    }
}
