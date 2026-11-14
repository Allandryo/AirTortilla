package AirTortilla.reservas.controller;

import AirTortilla.reservas.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UsuarioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Login fallido con credenciales no registradas o erróneas retorna 401")
    void testLoginFallido() throws Exception {
        Map<String, String> credenciales = Map.of(
                "username", "inexistente",
                "password", "clave_falsa"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credenciales)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje", containsString("Credenciales incorrectas")));
    }

    @Test
    @DisplayName("Login exitoso de ADMIN y NORMAL, verificación de sesión y logout")
    void testFlujoAutenticacionCompleto() throws Exception {
        // 1. Login exitoso como Admin
        MockHttpSession sessionAdmin = new MockHttpSession();
        Map<String, String> credencialesAdmin = Map.of(
                "username", "admin",
                "password", "admin123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .session(sessionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credencialesAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.username", is("admin")))
                .andExpect(jsonPath("$.usuario.rol", is("ADMIN")))
                .andExpect(jsonPath("$.usuario.esAdmin", is(true)));

        // 2. Verificar endpoint de sesión activa para Admin
        mockMvc.perform(get("/api/auth/session").session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado", is(true)))
                .andExpect(jsonPath("$.usuario.username", is("admin")));

        // 3. Login exitoso como Usuario Normal
        MockHttpSession sessionNormal = new MockHttpSession();
        Map<String, String> credencialesNormal = Map.of(
                "username", "usuario",
                "password", "user123"
        );

        mockMvc.perform(post("/api/auth/login")
                        .session(sessionNormal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credencialesNormal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.username", is("usuario")))
                .andExpect(jsonPath("$.usuario.rol", is("NORMAL")))
                .andExpect(jsonPath("$.usuario.esAdmin", is(false)));

        // 4. Logout invalida la sesión
        mockMvc.perform(post("/api/auth/logout").session(sessionAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session").session(sessionAdmin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Control de Acceso: Usuario normal no puede acceder a CRUD de usuarios (403)")
    void testUsuarioNormalNoPuedeGestionarUsuarios() throws Exception {
        // Simular sesión de usuario NORMAL
        MockHttpSession sessionNormal = new MockHttpSession();
        Usuario usuarioNormal = new Usuario(2L, "usuario", "user123", "Usuario Operador", "NORMAL");
        sessionNormal.setAttribute(AuthController.SESSION_USER_KEY, usuarioNormal);

        // Intentar listar usuarios
        mockMvc.perform(get("/api/usuarios").session(sessionNormal))
                .andExpect(status().isForbidden());

        // Intentar crear usuario
        Map<String, String> nuevo = Map.of(
                "username", "hack",
                "password", "123",
                "nombre", "Hacker",
                "rol", "ADMIN"
        );
        mockMvc.perform(post("/api/usuarios")
                        .session(sessionNormal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevo)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CRUD completo de usuarios por parte del Administrador")
    void testCrudUsuariosPorAdmin() throws Exception {
        MockHttpSession sessionAdmin = new MockHttpSession();
        Usuario admin = new Usuario(1L, "admin", "admin123", "Administrador General", "ADMIN");
        sessionAdmin.setAttribute(AuthController.SESSION_USER_KEY, admin);

        // 1. Listar usuarios
        mockMvc.perform(get("/api/usuarios").session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        // 2. Crear un nuevo usuario normal
        String nuevoUsername = "piloto_test";
        Map<String, String> nuevoUsuario = Map.of(
                "username", nuevoUsername,
                "password", "vuelo2026",
                "nombre", "Capitán Prueba",
                "rol", "NORMAL"
        );

        String respuestaCreacion = mockMvc.perform(post("/api/usuarios")
                        .session(sessionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoUsuario)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.username", is(nuevoUsername)))
                .andExpect(jsonPath("$.usuario.rol", is("NORMAL")))
                .andReturn().getResponse().getContentAsString();

        Number idCreado = com.jayway.jsonpath.JsonPath.read(respuestaCreacion, "$.usuario.id");

        // 3. Editar usuario creado
        Map<String, String> edicion = Map.of(
                "username", nuevoUsername,
                "nombre", "Capitán Prueba Editado",
                "rol", "NORMAL"
        );

        mockMvc.perform(put("/api/usuarios/" + idCreado)
                        .session(sessionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edicion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombre", is("Capitán Prueba Editado")));

        // 4. Eliminar el usuario creado
        mockMvc.perform(delete("/api/usuarios/" + idCreado).session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", containsString("eliminado correctamente")));

        // 5. El admin no puede eliminarse a sí mismo
        mockMvc.perform(delete("/api/usuarios/1").session(sessionAdmin))
                .andExpect(status().isBadRequest());
    }
}
