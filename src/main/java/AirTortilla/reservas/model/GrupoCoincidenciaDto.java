package AirTortilla.reservas.model;

import java.util.List;

/**
 * Agrupación de reservas donde coinciden Pasajero, Origen y Destino.
 */
public class GrupoCoincidenciaDto {
    private String clave;
    private String nombrePasajero;
    private String origen;
    private String destino;
    private String codigoPaisDestino;
    private int totalCoincidentes;
    private List<Reserva> reservas;

    public GrupoCoincidenciaDto() {
    }

    public GrupoCoincidenciaDto(String clave, String nombrePasajero, String origen,
                                String destino, String codigoPaisDestino,
                                int totalCoincidentes, List<Reserva> reservas) {
        this.clave = clave;
        this.nombrePasajero = nombrePasajero;
        this.origen = origen;
        this.destino = destino;
        this.codigoPaisDestino = codigoPaisDestino;
        this.totalCoincidentes = totalCoincidentes;
        this.reservas = reservas;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
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
        this.codigoPaisDestino = codigoPaisDestino;
    }

    public int getTotalCoincidentes() {
        return totalCoincidentes;
    }

    public void setTotalCoincidentes(int totalCoincidentes) {
        this.totalCoincidentes = totalCoincidentes;
    }

    public List<Reserva> getReservas() {
        return reservas;
    }

    public void setReservas(List<Reserva> reservas) {
        this.reservas = reservas;
    }
}
