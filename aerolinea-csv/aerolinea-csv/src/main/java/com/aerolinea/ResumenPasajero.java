package com.aerolinea;

import java.util.List;

public class ResumenPasajero {
    private String pasajero;
    private Double totalGastado;
    private int totalVuelos;
    private List<TramoVuelo> vuelos;

    public ResumenPasajero(String pasajero, Double totalGastado, int totalVuelos, List<TramoVuelo> vuelos) {
        this.pasajero = pasajero;
        this.totalGastado = totalGastado;
        this.totalVuelos = totalVuelos;
        this.vuelos = vuelos;
    }

    public String getPasajero() {
        return pasajero;
    }

    public Double getTotalGastado() {
        return totalGastado;
    }

    public int getTotalVuelos() {
        return totalVuelos;
    }

    public List<TramoVuelo> getVuelos() {
        return vuelos;
    }

    public static class TramoVuelo {
        private String codigoReserva;
        private String numeroVuelo;
        private String ruta;
        private String clase;
        private Double precio;

        public TramoVuelo(String codigoReserva, String numeroVuelo, String origen, String destino, String clase,
                Double precio) {
            this.codigoReserva = codigoReserva;
            this.numeroVuelo = numeroVuelo;
            this.ruta = origen + " ➔ " + destino;
            this.clase = clase;
            this.precio = precio;
        }

        public String getCodigoReserva() {
            return codigoReserva;
        }

        public String getNumeroVuelo() {
            return numeroVuelo;
        }

        public String getRuta() {
            return ruta;
        }

        public String getClase() {
            return clase;
        }

        public Double getPrecio() {
            return precio;
        }
    }
}