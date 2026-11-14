package AirTortilla.reservas.controller;

import AirTortilla.reservas.model.Usuario;
import AirTortilla.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservaRepository reservaRepository;

    @BeforeEach
    void limpiarBd() {
        reservaRepository.deleteAll();
    }

    private String fechaHoy() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
    }

    private MockHttpSession crearSesionUsuario(String rol) {
        MockHttpSession session = new MockHttpSession();
        boolean esAdmin = "ADMIN".equalsIgnoreCase(rol);
        Usuario usuario = new Usuario(
                esAdmin ? 1L : 2L,
                esAdmin ? "admin" : "usuario",
                esAdmin ? "admin123" : "user123",
                esAdmin ? "Administrador General" : "Usuario Operador",
                rol
        );
        session.setAttribute(AuthController.SESSION_USER_KEY, usuario);
        return session;
    }

    private static final String CSV_TEST_UPLOAD = """
            id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
            RES-01,Carlos Santana,Madrid,Roma,IT,2026-05-10,LOC-01,150.00,12A
            RES-02,Carlos Santana,Madrid,Roma,IT,2026-05-12,LOC-02,155.00,14C
            RES-03,Maria Lopez,Sevilla,Lisboa,PT,2026-08-01,LOC-03,90.00,05B
            """;

    @Test
    @DisplayName("Flujo completo: inicio vacío, subida de CSV, generación por destino y consulta Total y Línea a Línea")
    void testFlujoCompletoSubidaYConsultas() throws Exception {
        String fechaStr = fechaHoy();

        // 1. Al inicio antes de subir nada o tras consultar
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isOk());

        // 2. Subir archivo CSV con el formato requerido
        MockMultipartFile file = new MockMultipartFile(
                "archivo",
                "reservas_vuelos.csv",
                "text/csv",
                CSV_TEST_UPLOAD.getBytes()
        );

        mockMvc.perform(multipart("/api/reservas/subir").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(3)))
                .andExpect(jsonPath("$.totalArchivosGenerados", is(2)));

        // 3. Consultar reservas activas
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(3)))
                .andExpect(jsonPath("$.totalPaisesDestino", is(2)))
                .andExpect(jsonPath("$.reservas", hasSize(3)));

        // 3.1 Consultar reservas filtradas por país directamente desde la BD H2
        mockMvc.perform(get("/api/reservas?pais=IT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(2)))
                .andExpect(jsonPath("$.reservas", hasSize(2)))
                .andExpect(jsonPath("$.reservas[0].codigoPaisDestino", is("IT")));

        mockMvc.perform(get("/api/reservas/pais/PT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigoPaisDestino", is("PT")));

        // 4. Listar archivos generados
        mockMvc.perform(get("/api/reservas/archivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].nombreArchivo", startsWith("AirTortilla_")));

        // 5. Consultar Modalidad Total del archivo de Italia
        String nombreIt = "AirTortilla_IT_" + fechaStr + ".csv";
        mockMvc.perform(get("/api/reservas/archivos/" + nombreIt + "/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.nombreArchivo", is(nombreIt)))
                .andExpect(jsonPath("$.info.codigoPaisDestino", is("IT")))
                .andExpect(jsonPath("$.reservas.length()", is(2)))
                .andExpect(jsonPath("$.contenidoRaw", notNullValue()));

        // 6. Consultar Modalidad Línea a Línea del archivo de Italia
        mockMvc.perform(get("/api/reservas/archivos/" + nombreIt + "/linea?numero=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroLinea", is(1)))
                .andExpect(jsonPath("$.totalLineas", is(2)))
                .andExpect(jsonPath("$.reserva.nombrePasajero", is("Carlos Santana")))
                .andExpect(jsonPath("$.esCoincidente", is(true)))
                .andExpect(jsonPath("$.tieneSiguiente", is(true)))
                .andExpect(jsonPath("$.tieneAnterior", is(false)));

        // 7. Descargar archivo
        mockMvc.perform(get("/api/reservas/archivos/" + nombreIt + "/descargar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(nombreIt)));

        // 8. Limpiar archivos generados: bloqueado para anónimo / NORMAL y permitido solo para ADMIN
        mockMvc.perform(delete("/api/reservas/archivos"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/reservas/archivos").session(crearSesionUsuario("NORMAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("ADMIN")));

        mockMvc.perform(delete("/api/reservas/archivos").session(crearSesionUsuario("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", notNullValue()));
    }

    @Test
    @DisplayName("Debe rechazar subidas con reserva duplicada, mismo pasajero el mismo día o mismo asiento contra BD existente")
    void testRechazoConflictosYAppendContraBd() throws Exception {
        MockMultipartFile inicial = new MockMultipartFile(
                "archivo",
                "base.csv",
                "text/csv",
                CSV_TEST_UPLOAD.getBytes()
        );
        mockMvc.perform(multipart("/api/reservas/subir").file(inicial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(3)));

        // 1. Coincide en todos los campos con una reserva ya en BD
        String csvDuplicadoExacto = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-01,Carlos Santana,Madrid,Roma,IT,2026-05-10,LOC-01,150.00,12A
                """;
        MockMultipartFile fileDuplicado = new MockMultipartFile(
                "archivo", "duplicado.csv", "text/csv", csvDuplicadoExacto.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileDuplicado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", notNullValue()));

        // 2. Mismo pasajero volando dos veces el mismo día (2026-05-10)
        String csvMismoDia = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-98,Carlos Santana,Barcelona,Paris,FR,2026-05-10,LOC-98,130.00,04D
                """;
        MockMultipartFile fileMismoDia = new MockMultipartFile(
                "archivo", "mismo_dia.csv", "text/csv", csvMismoDia.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileMismoDia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("mismo día")));

        // 3. Otra persona ocupando el mismo asiento en el mismo vuelo y día
        String csvMismoAsiento = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-99,Pedro Picapiedra,Madrid,Roma,IT,2026-05-10,LOC-99,150.00,12A
                """;
        MockMultipartFile fileAsiento = new MockMultipartFile(
                "archivo", "mismo_asiento.csv", "text/csv", csvMismoAsiento.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileAsiento))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("asiento")));

        // 4. Subida válida adicional se añade sin borrar las anteriores
        String csvValido = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-04,Pedro Picapiedra,Madrid,Roma,IT,2026-05-10,LOC-04,150.00,12B
                """;
        MockMultipartFile fileValido = new MockMultipartFile(
                "archivo", "valido.csv", "text/csv", csvValido.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(4)))
                .andExpect(jsonPath("$.idsReasignados", is(0)));

        // 4.1 Otro usuario sube reserva nueva empezando de nuevo por RES-01 (ID se reasigna automáticamente) con localizador propio
        String csvOtroUsuarioMismoId = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-01,Lucia Fernandez,Barcelona,Berlin,DE,2026-07-20,LOC-NEW-01,210.00,08C
                """;
        MockMultipartFile fileOtroUsuario = new MockMultipartFile(
                "archivo", "otro_usuario.csv", "text/csv", csvOtroUsuarioMismoId.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileOtroUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(5)))
                .andExpect(jsonPath("$.idsReasignados", is(1)))
                .andExpect(jsonPath("$.filasOmitidas", is(0)))
                .andExpect(jsonPath("$.mensaje", containsString("reasignado")));

        // 4.2 Localizador ya existente en BD debe rechazarse como conflicto y omitirse
        String csvMismoLocalizador = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-97,Mario Casas,Valencia,Paris,FR,2026-08-20,LOC-01,140.00,09D
                """;
        MockMultipartFile fileMismoLocalizador = new MockMultipartFile(
                "archivo", "mismo_localizador.csv", "text/csv", csvMismoLocalizador.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileMismoLocalizador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("localizador")));

        // 4.3 CSV mixto: filas con conflicto (duplicada o localizador repetido) se omiten y la válida se guarda
        String csvMixto = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                RES-01,Carlos Santana,Madrid,Roma,IT,2026-05-10,LOC-01,150.00,12A
                RES-05,Elena Morales,Madrid,Lisboa,PT,2026-09-10,LOC-05,115.00,07C
                """;
        MockMultipartFile fileMixto = new MockMultipartFile(
                "archivo", "mixto.csv", "text/csv", csvMixto.getBytes());
        mockMvc.perform(multipart("/api/reservas/subir").file(fileMixto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(6)))
                .andExpect(jsonPath("$.nuevasAnadidas", is(1)))
                .andExpect(jsonPath("$.filasOmitidas", is(1)))
                .andExpect(jsonPath("$.detallesOmitidas", hasSize(1)))
                .andExpect(jsonPath("$.mensaje", containsString("omitida")));

        // 5. Vaciar BD: bloqueado sin sesión (401) y para usuario NORMAL (403); permitido solo para ADMIN
        mockMvc.perform(delete("/api/reservas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/reservas").session(crearSesionUsuario("NORMAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("ADMIN")));

        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(6)));

        mockMvc.perform(delete("/api/reservas").session(crearSesionUsuario("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", notNullValue()));

        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservas", is(0)));
    }
}
