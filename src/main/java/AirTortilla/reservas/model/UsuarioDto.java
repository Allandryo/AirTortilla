package AirTortilla.reservas.model;

/**
 * Data Transfer Object para exponer datos de usuario sin filtrar la contraseña.
 */
public class UsuarioDto {

    private Long id;
    private String username;
    private String nombre;
    private String rol;
    private boolean esAdmin;

    public UsuarioDto() {
    }

    public UsuarioDto(Long id, String username, String nombre, String rol) {
        this.id = id;
        this.username = username;
        this.nombre = nombre;
        this.rol = rol;
        this.esAdmin = "ADMIN".equalsIgnoreCase(rol);
    }

    public static UsuarioDto fromEntity(Usuario usuario) {
        if (usuario == null) return null;
        return new UsuarioDto(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getRol()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
        this.esAdmin = "ADMIN".equalsIgnoreCase(rol);
    }

    public boolean isEsAdmin() {
        return esAdmin;
    }

    public void setEsAdmin(boolean esAdmin) {
        this.esAdmin = esAdmin;
    }
}
