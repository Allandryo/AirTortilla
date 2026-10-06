package com.aerolinea;

import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VueloService {

    private List<ReservaVueloCsv> leerCsv() {
        ClassPathResource resource = new ClassPathResource("vuelos.csv");

        try (Reader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return new CsvToBeanBuilder<ReservaVueloCsv>(reader)
                    .withType(ReservaVueloCsv.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar el archivo CSV: " + e.getMessage(), e);
        }
    }

    public List<ReservaVueloCsv> obtenerTodosLosVuelos() {
        return leerCsv();
    }

    public List<ResumenPasajero> obtenerPasajerosConVuelos(
            String pasajero, String origen, String destino, String clase) {

        List<ReservaVueloCsv> filas = leerCsv();

        // Aplicar filtros
        if (pasajero != null && !pasajero.isBlank()) {
            String filtro = pasajero.toLowerCase();
            filas = filas.stream()
                    .filter(f -> f.getPasajero().toLowerCase().contains(filtro))
                    .collect(Collectors.toList());
        }
        if (origen != null && !origen.isBlank()) {
            String filtro = origen.toUpperCase();
            filas = filas.stream()
                    .filter(f -> f.getOrigen().equalsIgnoreCase(filtro))
                    .collect(Collectors.toList());
        }
        if (destino != null && !destino.isBlank()) {
            String filtro = destino.toUpperCase();
            filas = filas.stream()
                    .filter(f -> f.getDestino().equalsIgnoreCase(filtro))
                    .collect(Collectors.toList());
        }
        if (clase != null && !clase.isBlank()) {
            filas = filas.stream()
                    .filter(f -> f.getClase().equalsIgnoreCase(clase))
                    .collect(Collectors.toList());
        }

        Map<String, List<ReservaVueloCsv>> agrupado = filas.stream()
                .collect(Collectors.groupingBy(ReservaVueloCsv::getPasajero));

        return agrupado.entrySet().stream()
                .map(entry -> {
                    String nombre = entry.getKey();
                    List<ReservaVueloCsv> reservas = entry.getValue();

                    double gastoTotal = reservas.stream()
                            .mapToDouble(ReservaVueloCsv::getPrecio)
                            .sum();

                    List<ResumenPasajero.TramoVuelo> tramos = reservas.stream()
                            .map(r -> new ResumenPasajero.TramoVuelo(
                                    r.getCodigoReserva(),
                                    r.getNumeroVuelo(),
                                    r.getOrigen(),
                                    r.getDestino(),
                                    r.getClase(),
                                    r.getPrecio()))
                            .collect(Collectors.toList());

                    return new ResumenPasajero(nombre, gastoTotal, tramos.size(), tramos);
                })
                .sorted(Comparator.comparing(ResumenPasajero::getTotalGastado).reversed())
                .collect(Collectors.toList());
    }
}
