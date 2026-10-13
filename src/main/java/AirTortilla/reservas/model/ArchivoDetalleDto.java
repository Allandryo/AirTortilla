package AirTortilla.reservas.model;

import java.util.List;

/**
 * DTO para la consulta en modalidad "Total" de un archivo.
 */
public class ArchivoDetalleDto {
    private ArchivoReservaInfo info;
    private List<Reserva> reservas;
    private String contenidoRaw;
    private List<GrupoCoincidenciaDto> gruposCoincidentes;

    public ArchivoDetalleDto() {
    }

    public ArchivoDetalleDto(ArchivoReservaInfo info, List<Reserva> reservas, String contenidoRaw, List<GrupoCoincidenciaDto> gruposCoincidentes) {
        this.info = info;
        this.reservas = reservas;
        this.contenidoRaw = contenidoRaw;
        this.gruposCoincidentes = gruposCoincidentes;
    }

    public ArchivoReservaInfo getInfo() {
        return info;
    }

    public void setInfo(ArchivoReservaInfo info) {
        this.info = info;
    }

    public List<Reserva> getReservas() {
        return reservas;
    }

    public void setReservas(List<Reserva> reservas) {
        this.reservas = reservas;
    }

    public String getContenidoRaw() {
        return contenidoRaw;
    }

    public void setContenidoRaw(String contenidoRaw) {
        this.contenidoRaw = contenidoRaw;
    }

    public List<GrupoCoincidenciaDto> getGruposCoincidentes() {
        return gruposCoincidentes;
    }

    public void setGruposCoincidentes(List<GrupoCoincidenciaDto> gruposCoincidentes) {
        this.gruposCoincidentes = gruposCoincidentes;
    }
}
