package AirTortilla.reservas.service;

import AirTortilla.reservas.model.Usuario;
import AirTortilla.reservas.model.UsuarioDto;
import AirTortilla.reservas.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión, autenticación y operaciones CRUD sobre la tabla 'usuarios'.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Autentica a un usuario comprobando si está registrado en la base de datos
     * y si la contraseña coincide.
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> autenticar(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }

        return usuarioRepository.findByUsernameIgnoreCase(username.trim())
                .filter(u -> u.getPassword().equals(password));
    }

    /**
     * Lista todos los usuarios registrados en el sistema (sin exponer contraseñas).
     */
    @Transactional(readOnly = true)
    public List<UsuarioDto> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los datos de un usuario por su identificador único.
     */
    @Transactional(readOnly = true)
    public UsuarioDto obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con ID: " + id));
        return UsuarioDto.fromEntity(usuario);
    }

    /**
     * Registra un nuevo usuario en la base de datos (función exclusiva de administrador).
     */
    @Transactional
    public UsuarioDto crearUsuario(Usuario nuevoUsuario) {
        if (nuevoUsuario.getUsername() == null || nuevoUsuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario (username) es obligatorio.");
        }
        if (nuevoUsuario.getPassword() == null || nuevoUsuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        if (nuevoUsuario.getNombre() == null || nuevoUsuario.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre completo del usuario es obligatorio.");
        }

        String usernameLimpio = nuevoUsuario.getUsername().trim();
        if (usuarioRepository.existsByUsername(usernameLimpio)) {
            throw new IllegalArgumentException("El nombre de usuario '" + usernameLimpio + "' ya existe en la base de datos.");
        }

        String rolFinal = "ADMIN".equalsIgnoreCase(nuevoUsuario.getRol()) ? "ADMIN" : "NORMAL";

        Usuario usuario = new Usuario(
                usernameLimpio,
                nuevoUsuario.getPassword().trim(),
                nuevoUsuario.getNombre().trim(),
                rolFinal
        );

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioDto.fromEntity(guardado);
    }

    /**
     * Edita los datos de un usuario existente (función exclusiva de administrador).
     */
    @Transactional
    public UsuarioDto actualizarUsuario(Long id, Usuario datosActualizados) {
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el usuario con ID: " + id));

        if (datosActualizados.getUsername() != null && !datosActualizados.getUsername().isBlank()) {
            String nuevoUsername = datosActualizados.getUsername().trim();
            if (usuarioRepository.existsByUsernameAndIdNot(nuevoUsername, id)) {
                throw new IllegalArgumentException("El nombre de usuario '" + nuevoUsername + "' ya está ocupado por otra cuenta.");
            }
            usuarioExistente.setUsername(nuevoUsername);
        }

        if (datosActualizados.getNombre() != null && !datosActualizados.getNombre().isBlank()) {
            usuarioExistente.setNombre(datosActualizados.getNombre().trim());
        }

        // Si se suministra una nueva contraseña no vacía, se actualiza
        if (datosActualizados.getPassword() != null && !datosActualizados.getPassword().isBlank()) {
            usuarioExistente.setPassword(datosActualizados.getPassword().trim());
        }

        // Actualización de rol con comprobación de seguridad
        if (datosActualizados.getRol() != null && !datosActualizados.getRol().isBlank()) {
            String nuevoRol = datosActualizados.getRol().trim().toUpperCase();
            if (!"ADMIN".equals(nuevoRol) && !"NORMAL".equals(nuevoRol)) {
                nuevoRol = "NORMAL";
            }

            // Si el usuario era ADMIN y se cambia a NORMAL, asegurar que no sea el último ADMIN
            if ("ADMIN".equals(usuarioExistente.getRol()) && !"ADMIN".equals(nuevoRol)) {
                long totalAdmins = usuarioRepository.countByRol("ADMIN");
                if (totalAdmins <= 1) {
                    throw new IllegalArgumentException("No se puede degradar a rol NORMAL al único Administrador del sistema.");
                }
            }
            usuarioExistente.setRol(nuevoRol);
        }

        Usuario guardado = usuarioRepository.save(usuarioExistente);
        return UsuarioDto.fromEntity(guardado);
    }

    /**
     * Elimina un usuario por su identificador único (función exclusiva de administrador).
     */
    @Transactional
    public void eliminarUsuario(Long id, Long idUsuarioSesion) {
        if (id.equals(idUsuarioSesion)) {
            throw new IllegalArgumentException("No puedes eliminar tu propia cuenta mientras estás en sesión.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el usuario con ID: " + id));

        if ("ADMIN".equals(usuario.getRol())) {
            long totalAdmins = usuarioRepository.countByRol("ADMIN");
            if (totalAdmins <= 1) {
                throw new IllegalArgumentException("No es posible eliminar al único usuario Administrador registrado.");
            }
        }

        usuarioRepository.delete(usuario);
    }
}
