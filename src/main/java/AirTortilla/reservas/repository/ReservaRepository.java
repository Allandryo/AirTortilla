package AirTortilla.reservas.repository;

import AirTortilla.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio Spring Data JPA para la entidad Reserva.
 * Proporciona métodos CRUD completos y consultas derivadas automáticas.
 */
@Repository
public interface ReservaRepository extends JpaRepository<Reserva, String> {

    /**
     * Obtiene todas las reservas asociadas a un código de país de destino específico.
     *
     * @param codigoPaisDestino Código ISO de dos letras (ej: "ES", "FR", "IT").
     * @return Lista de reservas filtradas por país.
     */
    List<Reserva> findByCodigoPaisDestino(String codigoPaisDestino);

    /**
     * Busca reservas por coincidencia parcial en el nombre del pasajero (sin distinguir mayúsculas/minúsculas).
     *
     * @param nombrePasajero Nombre o fragmento a buscar.
     * @return Lista de reservas que coinciden.
     */
    List<Reserva> findByNombrePasajeroContainingIgnoreCase(String nombrePasajero);
}
