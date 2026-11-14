package AirTortilla.reservas.repository;

import AirTortilla.reservas.model.Reserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ReservaRepositoryTest {

    @Autowired
    private ReservaRepository reservaRepository;

    @Test
    @DisplayName("Debe persistir y recuperar una reserva por su ID")
    void testGuardarYBuscarPorId() {
        Reserva reserva = new Reserva(
                "RES-TEST-001",
                "Juan Perez",
                "Madrid",
                "Roma",
                "IT",
                LocalDate.of(2026, 6, 15),
                "LOC-TEST-01",
                120.50,
                "10A"
        );

        reservaRepository.save(reserva);

        Optional<Reserva> encontrada = reservaRepository.findById("RES-TEST-001");
        assertTrue(encontrada.isPresent());
        assertEquals("Juan Perez", encontrada.get().getNombrePasajero());
        assertEquals("IT", encontrada.get().getCodigoPaisDestino());
    }

    @Test
    @DisplayName("Debe buscar reservas filtradas por código de país de destino")
    void testFindByCodigoPaisDestino() {
        Reserva r1 = new Reserva("RES-ES-01", "Ana Gomez", "Sevilla", "Barcelona", "ES", LocalDate.now(), "LOC-01", 80.0, "1A");
        Reserva r2 = new Reserva("RES-FR-01", "Lucia Fernandez", "Madrid", "Paris", "FR", LocalDate.now(), "LOC-02", 110.0, "2B");
        Reserva r3 = new Reserva("RES-ES-02", "Pedro Soto", "Valencia", "Madrid", "ES", LocalDate.now(), "LOC-03", 75.0, "3C");

        reservaRepository.saveAll(List.of(r1, r2, r3));

        List<Reserva> espana = reservaRepository.findByCodigoPaisDestino("ES");
        assertEquals(2, espana.size());

        List<Reserva> francia = reservaRepository.findByCodigoPaisDestino("FR");
        assertEquals(1, francia.size());
    }

    @Test
    @DisplayName("Debe buscar reservas por coincidencia parcial en nombre de pasajero ignorando mayúsculas")
    void testFindByNombrePasajeroContainingIgnoreCase() {
        Reserva r1 = new Reserva("RES-101", "Carlos Santana", "Madrid", "Roma", "IT", LocalDate.now(), "LOC-101", 150.0, "12A");
        Reserva r2 = new Reserva("RES-102", "carlos alberto", "Madrid", "Lisboa", "PT", LocalDate.now(), "LOC-102", 90.0, "14B");
        Reserva r3 = new Reserva("RES-103", "Maria Lopez", "Bilbao", "Berlin", "DE", LocalDate.now(), "LOC-103", 130.0, "16C");

        reservaRepository.saveAll(List.of(r1, r2, r3));

        List<Reserva> resultado = reservaRepository.findByNombrePasajeroContainingIgnoreCase("carlos");
        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("Debe lanzar DataIntegrityViolationException ante el mismo pasajero en el mismo día")
    void testUniquePasajeroYFecha() {
        LocalDate fecha = LocalDate.of(2026, 6, 15);
        Reserva r1 = new Reserva("RES-U-01", "Laura Gomez", "Madrid", "Roma", "IT", fecha, "LOC-U01", 100.0, "01A");
        Reserva r2 = new Reserva("RES-U-02", "Laura Gomez", "Barcelona", "Paris", "FR", fecha, "LOC-U02", 120.0, "02B");

        reservaRepository.saveAndFlush(r1);
        assertThrows(DataIntegrityViolationException.class, () -> reservaRepository.saveAndFlush(r2));
    }

    @Test
    @DisplayName("Debe lanzar DataIntegrityViolationException ante el mismo asiento en el mismo vuelo y día")
    void testUniqueVueloYAsiento() {
        LocalDate fecha = LocalDate.of(2026, 6, 16);
        Reserva r1 = new Reserva("RES-S-01", "Laura Gomez", "Madrid", "Roma", "IT", fecha, "LOC-S01", 100.0, "12A");
        Reserva r2 = new Reserva("RES-S-02", "Mario Casas", "Madrid", "Roma", "IT", fecha, "LOC-S02", 100.0, "12a");

        reservaRepository.saveAndFlush(r1);
        assertThrows(DataIntegrityViolationException.class, () -> reservaRepository.saveAndFlush(r2));
    }

    @Test
    @DisplayName("Debe lanzar DataIntegrityViolationException ante un localizador duplicado")
    void testUniqueLocalizador() {
        LocalDate fecha1 = LocalDate.of(2026, 6, 17);
        LocalDate fecha2 = LocalDate.of(2026, 6, 18);
        Reserva r1 = new Reserva("RES-L-01", "Laura Gomez", "Madrid", "Roma", "IT", fecha1, "LOC-DUP-01", 100.0, "01A");
        Reserva r2 = new Reserva("RES-L-02", "Mario Casas", "Barcelona", "Paris", "FR", fecha2, "LOC-DUP-01", 120.0, "02B");

        reservaRepository.saveAndFlush(r1);
        assertThrows(DataIntegrityViolationException.class, () -> reservaRepository.saveAndFlush(r2));
    }
}
