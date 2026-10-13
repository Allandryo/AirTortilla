package AirTortilla.reservas.service;

import AirTortilla.reservas.model.ArchivoDetalleDto;
import AirTortilla.reservas.model.ArchivoReservaInfo;
import AirTortilla.reservas.model.LineaReservaDto;
import AirTortilla.reservas.model.Reserva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ReservaExportAndFileServiceTest {

    private ReservaParserService parserService;
    private ReservaExportService exportService;
    private ReservaFileService fileService;
    private Path tempDir;
    private String fechaHoy;

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
            RES-009,Elena Morales,Sevilla,Lisboa,PT,2026-04-18,LOC-010,95.00,07F
            """;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("airtortilla_test_output");
        parserService = new ReservaParserService();
        exportService = new ReservaExportService(tempDir.toString());
        fileService = new ReservaFileService(exportService, parserService);
        fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var s = Files.walk(tempDir)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    private List<Reserva> obtenerReservasPrueba() {
        InputStream is = new ByteArrayInputStream(CSV_TEST_DATA.getBytes(StandardCharsets.UTF_8));
        return parserService.parsearCsv(is);
    }

    @Test
    @DisplayName("Debe generar UN archivo por país de destino con formato AirTortilla_XX_YYYY_MM_DD.csv")
    void testFormatoNombreArchivosGenerados() {
        List<Reserva> reservas = obtenerReservasPrueba();

        List<ArchivoReservaInfo> archivos = exportService.exportarArchivosPorDestinoYFecha(reservas);
        assertFalse(archivos.isEmpty());

        // Verificar formato del nombre de todos los archivos generados
        for (ArchivoReservaInfo info : archivos) {
            String regex = "^AirTortilla_[A-Z]{2}_\\d{4}_\\d{2}_\\d{2}\\.csv$";
            assertTrue(info.getNombreArchivo().matches(regex),
                    "El nombre '" + info.getNombreArchivo() + "' debe coincidir con AirTortilla_XX_YYYY_MM_DD.csv");
            assertTrue(Files.exists(tempDir.resolve(info.getNombreArchivo())),
                    "El archivo físico debe existir en el disco");
        }

        // Verificar que hay exactamente un archivo por país, no duplicados por fecha
        Set<String> paisesEnArchivos = archivos.stream()
                .map(ArchivoReservaInfo::getCodigoPaisDestino)
                .collect(Collectors.toSet());
        assertEquals(paisesEnArchivos.size(), archivos.size(),
                "Debe haber exactamente un archivo por país de destino");

        // Verificar que existe el archivo para Italia con la fecha de hoy
        String nombreIt = "AirTortilla_IT_" + fechaHoy + ".csv";
        boolean existeIt = archivos.stream().anyMatch(a -> a.getNombreArchivo().equals(nombreIt));
        assertTrue(existeIt, "Debe existir " + nombreIt);

        // El archivo de Italia debe contener las 5 reservas de IT
        ArchivoReservaInfo itInfo = archivos.stream()
                .filter(a -> a.getCodigoPaisDestino().equals("IT"))
                .findFirst().orElseThrow();
        assertEquals(5, itInfo.getTotalReservas());
    }

    @Test
    @DisplayName("Debe consultar archivos en Modalidad Total correctamente")
    void testConsultarModalidadTotal() {
        List<Reserva> reservas = obtenerReservasPrueba();
        exportService.exportarArchivosPorDestinoYFecha(reservas);

        String nombreIt = "AirTortilla_IT_" + fechaHoy + ".csv";
        ArchivoDetalleDto detalle = fileService.consultarArchivoTotal(nombreIt);
        assertNotNull(detalle);
        assertEquals(nombreIt, detalle.getInfo().getNombreArchivo());
        assertEquals("IT", detalle.getInfo().getCodigoPaisDestino());
        assertEquals(5, detalle.getReservas().size());
        assertNotNull(detalle.getContenidoRaw());
        assertTrue(detalle.getContenidoRaw().contains("Carlos Santana"));

        // Comprobar detección de colisión dentro del archivo
        assertFalse(detalle.getGruposCoincidentes().isEmpty());
        assertEquals(5, detalle.getGruposCoincidentes().getFirst().getTotalCoincidentes());
    }

    @Test
    @DisplayName("Debe consultar archivos en Modalidad Línea a Línea con navegación correcta")
    void testConsultarModalidadLineaALinea() {
        List<Reserva> reservas = obtenerReservasPrueba();
        exportService.exportarArchivosPorDestinoYFecha(reservas);

        String nombreIt = "AirTortilla_IT_" + fechaHoy + ".csv";

        // Consultar línea 1
        LineaReservaDto linea1 = fileService.consultarArchivoLinea(nombreIt, 1);
        assertNotNull(linea1);
        assertEquals(1, linea1.getNumeroLinea());
        assertEquals(5, linea1.getTotalLineas());
        assertFalse(linea1.isTieneAnterior(), "La primera línea no debe tener anterior");
        assertTrue(linea1.isTieneSiguiente(), "La primera línea debe tener siguiente");
        assertNotNull(linea1.getReserva());
        assertEquals("Carlos Santana", linea1.getReserva().getNombrePasajero());
        assertTrue(linea1.isEsCoincidente());

        // Consultar última línea (5)
        LineaReservaDto linea5 = fileService.consultarArchivoLinea(nombreIt, 5);
        assertNotNull(linea5);
        assertEquals(5, linea5.getNumeroLinea());
        assertTrue(linea5.isTieneAnterior());
        assertFalse(linea5.isTieneSiguiente(), "La última línea no debe tener siguiente");
    }

    @Test
    @DisplayName("Vuelos a diferentes ciudades del mismo país de destino van en el mismo archivo")
    void testVuelosDistintasCiudadesMismoPaisVanEnMismoArchivo() {
        String csv = """
                id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
                DE-01,Pasajero 1,Madrid,Berlin,DE,2026-03-01,LOC-DE-01,120.0,1A
                DE-02,Pasajero 2,Barcelona,Munich,DE,2026-05-15,LOC-DE-02,130.0,2B
                DE-03,Pasajero 3,Valencia,Frankfurt,DE,2026-07-20,LOC-DE-03,140.0,3C
                """;
        List<Reserva> reservas = parserService.parsearCsvTexto(csv);
        List<ArchivoReservaInfo> archivos = exportService.exportarArchivosPorDestinoYFecha(reservas);

        assertEquals(1, archivos.size(), "Debe haber exactamente 1 archivo generado para el país de destino DE");
        ArchivoReservaInfo infoDe = archivos.getFirst();
        assertEquals("DE", infoDe.getCodigoPaisDestino());
        assertEquals(3, infoDe.getTotalReservas());

        ArchivoDetalleDto detalle = fileService.consultarArchivoTotal(infoDe.getNombreArchivo());
        assertEquals(3, detalle.getReservas().size());
        assertTrue(detalle.getContenidoRaw().contains("Berlin"));
        assertTrue(detalle.getContenidoRaw().contains("Munich"));
        assertTrue(detalle.getContenidoRaw().contains("Frankfurt"));
    }
}
