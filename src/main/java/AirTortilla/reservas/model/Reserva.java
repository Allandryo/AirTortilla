package AirTortilla.reservas.model;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa una reserva en el sistema de AirTortilla.
 * Mapeada directamente desde archivo CSV mediante OpenCSV (CsvToBean)
 * y persistida como entidad relacional mediante Spring Data JPA / Hibernate.
 */
@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @Column(name = "id", length = 60, nullable = false)
    @CsvBindByName(column = "id", required = false)
    private String id;

    @Column(name = "nombre_pasajero", nullable = false)
    @CsvBindByName(column = "nombre_pasajero", required = true)
    private String nombrePasajero;

    @Column(name = "origen", nullable = false)
    @CsvBindByName(column = "origen", required = true)
    private String origen;

    @Column(name = "destino", nullable = false)
    @CsvBindByName(column = "destino", required = true)
    private String destino;

    @Column(name = "pais_destino", length = 10, nullable = false)
    @CsvBindByName(column = "pais_destino", required = true)
    private String codigoPaisDestino; // Código ISO de 2 letras (ej. "FR", "IT")

    @Column(name = "fecha_reserva", nullable = false)
    @CsvBindByName(column = "fecha_reserva", required = true)
    @CsvDate(value = "yyyy-MM-dd")
    private LocalDate fechaReserva;

    @Column(name = "localizador", length = 50)
    @CsvBindByName(column = "localizador", required = false)
    private String localizador;

    @Column(name = "precio")
    @CsvBindByName(column = "precio", required = false)
    private Double precio;

    @Column(name = "asiento", length = 20)
    @CsvBindByName(column = "asiento", required = false)
    private String asiento;

    public Reserva() {
    }

    public Reserva(String id, String nombrePasajero, String origen, String destino,
                   String codigoPaisDestino, LocalDate fechaReserva, String localizador,
                   Double precio, String asiento) {
        this.id = id;
        this.nombrePasajero = nombrePasajero;
        this.origen = origen;
        this.destino = destino;
        this.codigoPaisDestino = codigoPaisDestino;
        this.fechaReserva = fechaReserva;
        this.localizador = localizador;
        this.precio = precio;
        this.asiento = asiento;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombrePasajero() {
        return nombrePasajero;
    }

    public void setNombrePasajero(String nombrePasajero) {
        this.nombrePasajero = nombrePasajero;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getCodigoPaisDestino() {
        return codigoPaisDestino;
    }

    public void setCodigoPaisDestino(String codigoPaisDestino) {
        this.codigoPaisDestino = codigoPaisDestino != null ? codigoPaisDestino.trim().toUpperCase() : null;
    }

    public LocalDate getFechaReserva() {
        return fechaReserva;
    }

    public void setFechaReserva(LocalDate fechaReserva) {
        this.fechaReserva = fechaReserva;
    }

    public String getLocalizador() {
        return localizador;
    }

    public void setLocalizador(String localizador) {
        this.localizador = localizador;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public String getAsiento() {
        return asiento;
    }

    public void setAsiento(String asiento) {
        this.asiento = asiento;
    }

    /**
     * Delimitador utilizado para construir la clave de agrupamiento por colisión.
     */
    public static final String SEPARADOR_COINCIDENCIA = "||";

    /**
     * Genera una clave compuesta inequívoca para agrupar reservas:
     * Nombre del Pasajero + Origen + Destino (normalizados en minúsculas y sin espacios residuales).
     *
     * @return Clave compuesta normalizada para detectar pasajeros coincidentes.
     */
    public String getClaveCoincidencia() {
        String n = nombrePasajero != null ? nombrePasajero.trim().toLowerCase() : "";
        String o = origen != null ? origen.trim().toLowerCase() : "";
        String d = destino != null ? destino.trim().toLowerCase() : "";
        return n + SEPARADOR_COINCIDENCIA + o + SEPARADOR_COINCIDENCIA + d;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reserva reserva = (Reserva) o;
        return Objects.equals(id, reserva.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reserva{" +
                "id='" + id + '\'' +
                ", nombrePasajero='" + nombrePasajero + '\'' +
                ", origen='" + origen + '\'' +
                ", destino='" + destino + '\'' +
                ", codigoPaisDestino='" + codigoPaisDestino + '\'' +
                ", fechaReserva=" + fechaReserva +
                ", localizador='" + localizador + '\'' +
                '}';
    }
}
