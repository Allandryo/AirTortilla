package AirTortilla.reservas.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    private String fechaHoy() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
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

        // 8. Limpiar archivos generados
        mockMvc.perform(delete("/api/reservas/archivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", notNullValue()));
    }
}
