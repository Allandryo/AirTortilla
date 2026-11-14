package AirTortilla.reservas.repository;

import AirTortilla.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 1. Métodos automáticos incluidos (sin escribir una sola línea de SQL):
 * - findAll(): Equivale a "SELECT * FROM reservas;"
 * - findById(id): Equivale a "SELECT * FROM reservas WHERE id = ?;"
 * - save(reserva): Inserta o actualiza una reserva (INSERT / UPDATE).
 * - saveAll(lista): Guarda una lista entera de reservas en la base de datos.
 * - deleteById(id): Borra una reserva por su ID.
 * - deleteAll(): Borra todas las reservas de la tabla.
 *
 * 2. Consultas por convención de nombre (Query Methods):
 * Spring lee el nombre del método y crea la consulta SQL por nosotros:
 * - findByCodigoPaisDestino(...) -> Busca por la columna 'pais_destino'.
 * - findByNombrePasajeroContainingIgnoreCase(...) -> Busca texto dentro del
 * nombre ignorando mayúsculas/minúsculas.
 */
@Repository
public interface ReservaRepository extends JpaRepository<Reserva, String> {
    List<Reserva> findByCodigoPaisDestino(String codigoPaisDestino);

    List<Reserva> findByNombrePasajeroContainingIgnoreCase(String nombrePasajero);
}
