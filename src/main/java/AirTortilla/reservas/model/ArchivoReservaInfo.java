package AirTortilla.reservas.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Metadatos de un fichero de reservas generado por país de destino y fecha.
 */
public class ArchivoReservaInfo {
    private String nombreArchivo;
    private String codigoPaisDestino;
    private LocalDate fechaReserva;
    private int totalReservas;
    private long tamanoBytes;
    private LocalDateTime fechaGeneracion;

    public ArchivoReservaInfo() {
    }

    public ArchivoReservaInfo(String nombreArchivo, String codigoPaisDestino, LocalDate fechaReserva,
                              int totalReservas, long tamanoBytes, LocalDateTime fechaGeneracion) {
        this.nombreArchivo = nombreArchivo;
        this.codigoPaisDestino = codigoPaisDestino;
        this.fechaReserva = fechaReserva;
        this.totalReservas = totalReservas;
        this.tamanoBytes = tamanoBytes;
        this.fechaGeneracion = fechaGeneracion;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getCodigoPaisDestino() {
        return codigoPaisDestino;
    }

    public void setCodigoPaisDestino(String codigoPaisDestino) {
        this.codigoPaisDestino = codigoPaisDestino;
    }

    public LocalDate getFechaReserva() {
        return fechaReserva;
    }

    public void setFechaReserva(LocalDate fechaReserva) {
        this.fechaReserva = fechaReserva;
    }

    public int getTotalReservas() {
        return totalReservas;
    }

    public void setTotalReservas(int totalReservas) {
        this.totalReservas = totalReservas;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public void setTamanoBytes(long tamanoBytes) {
        this.tamanoBytes = tamanoBytes;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }
}
