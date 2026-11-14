package AirTortilla.reservas.service;

import AirTortilla.reservas.model.Usuario;
import AirTortilla.reservas.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Inicializador de datos predeterminados en la base de datos H2.
 * Garantiza la existencia de al menos un usuario Administrador y un Usuario Normal.
 */
@Component
public class UsuarioDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioDataInitializer.class);

    private final UsuarioRepository usuarioRepository;

    public UsuarioDataInitializer(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        // Inicializar Administrador predeterminado
        if (!usuarioRepository.existsByUsername("admin")) {
            Usuario admin = new Usuario(
                    "admin",
                    "admin123",
                    "Administrador General",
                    "ADMIN"
            );
            usuarioRepository.save(admin);
            log.info(">> [BD H2 - Tabla usuarios] Creado usuario ADMIN inicial: 'admin' (Clave: 'admin123')");
        } else {
            log.info(">> [BD H2 - Tabla usuarios] Usuario 'admin' ya existente en la base de datos.");
        }

        // Inicializar Usuario Normal predeterminado
        if (!usuarioRepository.existsByUsername("usuario")) {
            Usuario normal = new Usuario(
                    "usuario",
                    "user123",
                    "Usuario Operador",
                    "NORMAL"
            );
            usuarioRepository.save(normal);
            log.info(">> [BD H2 - Tabla usuarios] Creado usuario NORMAL inicial: 'usuario' (Clave: 'user123')");
        } else {
            log.info(">> [BD H2 - Tabla usuarios] Usuario 'usuario' ya existente en la base de datos.");
        }
    }
}
