package com.aerolinea;

import com.opencsv.bean.CsvBindByName;

public class ReservaVueloCsv {
    @CsvBindByName(column = "pasajero")
    private String pasajero;

    @CsvBindByName(column = "codigoReserva")
    private String codigoReserva;

    @CsvBindByName(column = "numeroVuelo")
    private String numeroVuelo;

    @CsvBindByName(column = "origen")
    private String origen;

    @CsvBindByName(column = "destino")
    private String destino;

    @CsvBindByName(column = "clase")
    private String clase;

    @CsvBindByName(column = "precio")
    private Double precio;

    public ReservaVueloCsv() {
    }

    public String getPasajero() {
        return pasajero;
    }

    public void setPasajero(String pasajero) {
        this.pasajero = pasajero;
    }

    public String getCodigoReserva() {
        return codigoReserva;
    }

    public void setCodigoReserva(String codigoReserva) {
        this.codigoReserva = codigoReserva;
    }

    public String getNumeroVuelo() {
        return numeroVuelo;
    }

    public void setNumeroVuelo(String numeroVuelo) {
        this.numeroVuelo = numeroVuelo;
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

    public String getClase() {
        return clase;
    }

    public void setClase(String clase) {
        this.clase = clase;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }
}