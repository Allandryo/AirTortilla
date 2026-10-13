package AirTortilla.reservas.service;

import AirTortilla.reservas.model.GrupoCoincidenciaDto;
import AirTortilla.reservas.model.Reserva;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio encargado de parsear archivos CSV a objetos Reserva utilizando OpenCSV 5.9 (CsvToBean),
 * y de validar la unicidad e identificación inequívoca de las reservas y sus localizadores.
 */
@Service
public class ReservaParserService {

    private static final Logger log = LoggerFactory.getLogger(ReservaParserService.class);

    /**
     * Parsea un flujo CSV a una lista de objetos Reserva usando OpenCSV (CsvToBean).
     */
    public List<Reserva> parsearCsv(InputStream inputStream) {
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parsearDesdeReader(reader);
        } catch (Exception e) {
            log.error("Error al leer el flujo CSV", e);
            throw new IllegalArgumentException("No se pudo procesar el archivo CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Parsea una cadena CSV a una lista de objetos Reserva.
     */
    public List<Reserva> parsearCsvTexto(String contenidoCsv) {
        try (Reader reader = new StringReader(contenidoCsv)) {
            return parsearDesdeReader(reader);
        } catch (Exception e) {
            log.error("Error al parsear el contenido CSV", e);
            throw new IllegalArgumentException("Error al parsear el contenido CSV: " + e.getMessage(), e);
        }
    }

    private List<Reserva> parsearDesdeReader(Reader reader) {
        HeaderColumnNameMappingStrategy<Reserva> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(Reserva.class);

        CsvToBean<Reserva> csvToBean = new CsvToBeanBuilder<Reserva>(reader)
                .withMappingStrategy(strategy)
                .withIgnoreLeadingWhiteSpace(true)
                .withIgnoreEmptyLine(true)
                .withThrowExceptions(false) // Permite capturar errores de formato suavemente
                .build();

        List<Reserva> reservas = csvToBean.parse();

        // Enriquecer, validar y asegurar identificación inequívoca y localizadores
        normalizarYAsegurarIdentificadores(reservas);

        log.info("Parseadas {} reservas correctamente con OpenCSV", reservas.size());
        return reservas;
    }

    /**
     * Garantiza que cada reserva posea:
     * 1) Identificador único e inequívoco (si viniera vacío).
     * 2) Localizador único y persistente, especialmente crucial cuando 2 o más pasajeros
     *    coinciden en Nombre, Origen y Destino.
     * 3) Código ISO de país en mayúsculas y normalizado.
     */
    public void normalizarYAsegurarIdentificadores(List<Reserva> reservas) {
        if (reservas == null || reservas.isEmpty()) {
            return;
        }

        // Mapa para verificar unicidad de ID y Localizador
        Set<String> idsExistentes = new HashSet<>();
        Set<String> localizadoresExistentes = new HashSet<>();

        // Detectar colisiones de (Nombre + Origen + Destino)
        Map<String, List<Reserva>> agrupacionCoincidentes = new HashMap<>();

        for (int i = 0; i < reservas.size(); i++) {
            Reserva r = reservas.get(i);

            // Validar campos esenciales
            if (r.getCodigoPaisDestino() != null) {
                r.setCodigoPaisDestino(r.getCodigoPaisDestino().trim().toUpperCase());
            } else {
                // Por defecto o derivado de destino si faltase
                r.setCodigoPaisDestino("XX");
            }

            if (r.getFechaReserva() == null) {
                r.setFechaReserva(LocalDate.now());
            }

            // Asegurar ID inequívoco
            if (r.getId() == null || r.getId().trim().isEmpty() || idsExistentes.contains(r.getId())) {
                String nuevoId = "RES-" + r.getCodigoPaisDestino() + "-" + String.format("%04d", (i + 1)) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                r.setId(nuevoId);
            }
            idsExistentes.add(r.getId());

            agrupacionCoincidentes
                    .computeIfAbsent(r.getClaveCoincidencia(), k -> new ArrayList<>())
                    .add(r);
        }

        // Asignar o asegurar localizadores inequívocos, especialmente en grupos coincidentes
        for (Map.Entry<String, List<Reserva>> entry : agrupacionCoincidentes.entrySet()) {
            List<Reserva> grupo = entry.getValue();
            boolean esGrupoCoincidente = grupo.size() > 1;

            for (int subIdx = 0; subIdx < grupo.size(); subIdx++) {
                Reserva r = grupo.get(subIdx);
                // Si el localizador viene nulo, vacío, o colisiona con otro existente en el conjunto
                if (r.getLocalizador() == null || r.getLocalizador().trim().isEmpty()
                        || localizadoresExistentes.contains(r.getLocalizador())) {
                    String nuevoLocalizador = generarLocalizadorUnico(r, subIdx + 1, esGrupoCoincidente);
                    while (localizadoresExistentes.contains(nuevoLocalizador)) {
                        nuevoLocalizador = generarLocalizadorUnico(r, subIdx + (int)(Math.random() * 1000) + 1, esGrupoCoincidente);
                    }
                    r.setLocalizador(nuevoLocalizador);
                }
                localizadoresExistentes.add(r.getLocalizador());
            }
        }
    }

    private String generarLocalizadorUnico(Reserva r, int indice, boolean esCoincidente) {
        String prefijo = "AT-" + (r.getCodigoPaisDestino() != null ? r.getCodigoPaisDestino() : "XX");
        String letra = String.valueOf((char) ('A' + (indice % 26)));
        String sufijo = esCoincidente ? "-C" + indice : "-" + UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        return prefijo + sufijo + letra;
    }

    /**
     * Extrae los grupos de reservas que coinciden en Nombre, Origen y Destino.
     */
    public List<GrupoCoincidenciaDto> analizarCoincidencias(List<Reserva> reservas) {
        if (reservas == null || reservas.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Reserva>> agrupados = reservas.stream()
                .collect(Collectors.groupingBy(Reserva::getClaveCoincidencia, LinkedHashMap::new, Collectors.toList()));

        List<GrupoCoincidenciaDto> coincidencias = new ArrayList<>();

        for (Map.Entry<String, List<Reserva>> entry : agrupados.entrySet()) {
            List<Reserva> lista = entry.getValue();
            if (lista.size() > 1) { // 2 o más pasajeros coincidentes
                Reserva muestra = lista.getFirst();
                coincidencias.add(new GrupoCoincidenciaDto(
                        entry.getKey(),
                        muestra.getNombrePasajero(),
                        muestra.getOrigen(),
                        muestra.getDestino(),
                        muestra.getCodigoPaisDestino(),
                        lista.size(),
                        lista
                ));
            }
        }

        // Ordenar con el grupo de más coincidentes primero
        coincidencias.sort((a, b) -> Integer.compare(b.getTotalCoincidentes(), a.getTotalCoincidentes()));
        return coincidencias;
    }
}
