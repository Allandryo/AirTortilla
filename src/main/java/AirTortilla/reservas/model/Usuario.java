package AirTortilla.reservas.model;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * Entidad que representa a un usuario en el sistema de AirTortilla.
 * Mapeada directamente a la tabla relacional 'usuarios' en la Base de Datos H2.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "password", length = 100, nullable = false)
    private String password;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    /**
     * Rol del usuario:
     * - "ADMIN": Puede usar la página, y además crear, editar o eliminar usuarios.
     * - "NORMAL": Solo puede usar la página (consultar, subir CSV, generar y descargar archivos).
     */
    @Column(name = "rol", length = 20, nullable = false)
    private String rol;

    public Usuario() {
    }

    public Usuario(String username, String password, String nombre, String rol) {
        this.username = username;
        this.password = password;
        this.nombre = nombre;
        this.rol = (rol != null) ? rol.toUpperCase() : "NORMAL";
    }

    public Usuario(Long id, String username, String password, String nombre, String rol) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nombre = nombre;
        this.rol = (rol != null) ? rol.toUpperCase() : "NORMAL";
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
        this.username = (username != null) ? username.trim() : null;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = (nombre != null) ? nombre.trim() : null;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = (rol != null) ? rol.trim().toUpperCase() : "NORMAL";
    }

    public boolean esAdmin() {
        return "ADMIN".equalsIgnoreCase(this.rol);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(id, usuario.id) || Objects.equals(username, usuario.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", nombre='" + nombre + '\'' +
                ", rol='" + rol + '\'' +
                '}';
    }
}
