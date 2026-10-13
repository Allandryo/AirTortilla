package AirTortilla.reservas.service;

import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.Reserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReservaParserServiceTest {

    private final ReservaParserService parserService = new ReservaParserService();

    private static final String CSV_TEST_DATA = """
            id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
            RES-001,Carlos Santana,Madrid,Roma,IT,2026-05-10,LOC-001,150.00,12A
            RES-002,Carlos Santana,Madrid,Roma,IT,2026-05-12,LOC-002,155.00,14C
            RES-003,Carlos Santana,Madrid,Roma,IT,2026-06-01,LOC-003,160.00,10B
            RES-004,Carlos Santana,Madrid,Roma,IT,2026-07-15,LOC-004,145.00,08F
            RES-005,Carlos Santana,Madrid,Roma,IT,2026-08-20,LOC-005,170.00,15A
            RES-006,Ana Gomez,Barcelona,Paris,FR,2026-06-10,LOC-006,120.00,03B
            RES-007,Ana Gomez,Barcelona,Paris,FR,2026-07-11,LOC-007,125.00,04C
            RES-008,Luis Martinez,Valencia,Berlin,DE,2026-09-01,LOC-008,180.00,19D
            RES-009,Luis Martinez,Valencia,Berlin,DE,2026-09-15,LOC-009,190.00,21A
            RES-010,Elena Morales,Sevilla,Lisboa,PT,2026-04-18,LOC-010,95.00,07F
            """;

    @Test
    @DisplayName("Debe parsear correctamente el formato id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento")
    void testParsearCsvFormatoCompleto() {
        InputStream is = new ByteArrayInputStream(CSV_TEST_DATA.getBytes(StandardCharsets.UTF_8));
        List<Reserva> reservas = parserService.parsearCsv(is);

        assertNotNull(reservas);
        assertEquals(10, reservas.size(), "Debe haber parseado las 10 filas");

        Reserva primera = reservas.getFirst();
        assertEquals("RES-001", primera.getId());
        assertEquals("Carlos Santana", primera.getNombrePasajero());
        assertEquals("Madrid", primera.getOrigen());
        assertEquals("Roma", primera.getDestino());
        assertEquals("IT", primera.getCodigoPaisDestino());
        assertNotNull(primera.getFechaReserva());
        assertEquals("LOC-001", primera.getLocalizador());
        assertEquals(150.00, primera.getPrecio());
        assertEquals("12A", primera.getAsiento());
    }

    @Test
    @DisplayName("Debe detectar grupos de coincidencia (mismo nombre, origen y destino) y asignar localizadores únicos")
    void testCasosDeColisionYLocalizador() {
        List<Reserva> reservas = parserService.parsearCsvTexto(CSV_TEST_DATA);
        List<GrupoCoincidenciaDto> grupos = parserService.analizarCoincidencias(reservas);

        // Debe haber al menos 3 grupos con colisiones (Carlos Santana en Roma, Ana Gomez en Paris, Luis Martinez en Berlin)
        assertTrue(grupos.size() >= 3, "Debe haber al menos 3 grupos coincidentes");

        // Uno de ellos debe tener al menos 5 pasajeros coincidentes (Carlos Santana)
        boolean existeGrupoConAlMenos5 = grupos.stream().anyMatch(g -> g.getTotalCoincidentes() >= 5);
        assertTrue(existeGrupoConAlMenos5, "Debe existir al menos un grupo con 5 coincidentes");

        GrupoCoincidenciaDto grupo5 = grupos.stream()
                .filter(g -> g.getTotalCoincidentes() >= 5)
                .findFirst()
                .orElse(null);
        assertNotNull(grupo5);
        assertEquals("Carlos Santana", grupo5.getNombrePasajero());
        assertEquals("Madrid", grupo5.getOrigen());
        assertEquals("Roma", grupo5.getDestino());

        Set<String> localizadores = new HashSet<>();
        for (Reserva r : grupo5.getReservas()) {
            assertNotNull(r.getLocalizador());
            assertTrue(localizadores.add(r.getLocalizador()), "Cada localizador en el grupo debe ser único");
        }
    }

    @Test
    @DisplayName("Debe autogenerar ID inequívoco y localizador si faltan en el archivo CSV")
    void testAutogeneracionIdYLocalizador() {
        String csvSinIdNiLocalizador = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                ,Pedro Almodovar,Madrid,Venecia,IT,2026-08-10,,110.0,01A
                ,Pedro Almodovar,Madrid,Venecia,IT,2026-08-10,,110.0,01B
                """;

        List<Reserva> reservas = parserService.parsearCsvTexto(csvSinIdNiLocalizador);
        assertEquals(2, reservas.size());

        Reserva r1 = reservas.get(0);
        Reserva r2 = reservas.get(1);

        assertNotNull(r1.getId());
        assertNotNull(r2.getId());
        assertNotEquals(r1.getId(), r2.getId(), "Los IDs autogenerados deben ser distintos e inequívocos");

        assertNotNull(r1.getLocalizador());
        assertNotNull(r2.getLocalizador());
        assertNotEquals(r1.getLocalizador(), r2.getLocalizador(), "Los localizadores deben ser distintos e inequívocos");
    }
}
