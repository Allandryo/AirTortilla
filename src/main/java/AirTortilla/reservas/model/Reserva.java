package AirTortilla.reservas.model;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;

/**
 * ¿QUÉ ES ESTA CLASE Y CÓMO FUNCIONA? (Explicación para principiantes)
 * 
 * Esta clase cumple un DOBLE papel en el programa:
 *
 * 1. ES UNA ENTIDAD DE BASE DE DATOS (JPA / Hibernate):
 *    - La anotación @Entity le dice a Spring: "Esta clase representa una tabla en la BD".
 *    - @Table(name = "reservas") indica que la tabla se llama "reservas".
 *    - Cada atributo (id, nombrePasajero, etc.) se corresponde con una columna (@Column).
 *    - @Id marca la clave primaria de la tabla (el identificador único).
 *    - Los 'uniqueConstraints' definen reglas en la BD para evitar duplicados:
 *        * Un pasajero no puede tener dos reservas el mismo día.
 *        * Un asiento no puede ocuparse dos veces en el mismo vuelo y día.
 *        * Un localizador no puede repetirse.
 *
 * 2. ES EL MODELO DE LECTURA DEL ARCHIVO CSV (OpenCSV):
 *    - Las anotaciones @CsvBindByName(column = "nombre") le dicen a la librería OpenCSV
 *      con qué columna del archivo CSV se debe rellenar cada campo.
 *    - Por ejemplo: @CsvBindByName(column = "precio") lee el precio del archivo y lo guarda
 *      en la variable 'precio'.
 */
@Entity
@Table(
    name = "reservas",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_reserva_pasajero_fecha", columnNames = {"nombre_pasajero", "fecha_reserva"}),
        @UniqueConstraint(name = "uk_reserva_vuelo_asiento", columnNames = {"origen", "destino", "fecha_reserva", "asiento"}),
        @UniqueConstraint(name = "uk_reserva_localizador", columnNames = {"localizador"})
    }
)
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
        setId(id);
        setNombrePasajero(nombrePasajero);
        setOrigen(origen);
        setDestino(destino);
        setCodigoPaisDestino(codigoPaisDestino);
        setFechaReserva(fechaReserva);
        setLocalizador(localizador);
        setPrecio(precio);
        setAsiento(asiento);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id != null && !id.isBlank()) ? id.trim() : null;
    }

    public String getNombrePasajero() {
        return nombrePasajero;
    }

    public void setNombrePasajero(String nombrePasajero) {
        this.nombrePasajero = nombrePasajero != null ? nombrePasajero.trim() : null;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen != null ? origen.trim() : null;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino != null ? destino.trim() : null;
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
        this.localizador = (localizador != null && !localizador.isBlank()) ? localizador.trim() : null;
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
        this.asiento = (asiento != null && !asiento.isBlank()) ? asiento.trim().toUpperCase() : null;
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
