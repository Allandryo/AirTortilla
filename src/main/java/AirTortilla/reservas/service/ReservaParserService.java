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

    public static final String PAIS_DESCONOCIDO = "XX";
    private static final String PREFIJO_ID_RESERVA = "RES-";
    private static final String PREFIJO_LOCALIZADOR = "AT-";

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
     * Garantiza que cada reserva cumpla con las reglas de negocio de la aerolínea:
     * 1) Código ISO de país en mayúsculas y normalizado (por defecto 'XX' si viniera nulo).
     * 2) Identificador único e inequívoco (autogenerado si viniera vacío o duplicado).
     * 3) Localizador único y persistente, desambiguando especialmente cuando 2 o más
     *    pasajeros coinciden en Nombre, Origen y Destino.
     *
     * @param reservas Lista de reservas leídas desde el CSV.
     */
    public void normalizarYAsegurarIdentificadores(List<Reserva> reservas) {
        if (reservas == null || reservas.isEmpty()) {
            return;
        }

        Set<String> idsExistentes = new HashSet<>();
        Set<String> localizadoresExistentes = new HashSet<>();

        // Paso 1: Normalizar campos y asegurar ID inequívoco para cada fila
        for (int i = 0; i < reservas.size(); i++) {
            normalizarCamposYAsegurarId(reservas.get(i), i + 1, idsExistentes);
        }

        // Paso 2: Agrupar por coincidencia (Nombre + Origen + Destino)
        Map<String, List<Reserva>> agrupacionCoincidentes = agruparPorCoincidencia(reservas);

        // Paso 3: Asignar o asegurar localizadores únicos y desambiguados por grupo
        resolverLocalizadores(agrupacionCoincidentes, localizadoresExistentes);
    }

    /**
     * Normaliza los atributos indispensables de una reserva (país, fecha) y garantiza
     * la asignación de un identificador único en caso de ausencia o duplicidad.
     */
    private void normalizarCamposYAsegurarId(Reserva r, int posicionFila, Set<String> idsExistentes) {
        String pais = r.getCodigoPaisDestino();
        r.setCodigoPaisDestino((pais != null && !pais.isBlank()) ? pais.trim().toUpperCase() : PAIS_DESCONOCIDO);

        if (r.getFechaReserva() == null) {
            r.setFechaReserva(LocalDate.now());
        }

        if (r.getId() == null || r.getId().isBlank() || idsExistentes.contains(r.getId())) {
            String sufijo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            r.setId(String.format("%s%s-%04d-%s", PREFIJO_ID_RESERVA, r.getCodigoPaisDestino(), posicionFila, sufijo));
        }
        idsExistentes.add(r.getId());
    }

    /**
     * Agrupa la colección de reservas en base a la clave compuesta de coincidencia:
     * Nombre Pasajero + Origen + Destino.
     */
    private Map<String, List<Reserva>> agruparPorCoincidencia(List<Reserva> reservas) {
        Map<String, List<Reserva>> agrupacion = new LinkedHashMap<>();
        for (Reserva r : reservas) {
            agrupacion.computeIfAbsent(r.getClaveCoincidencia(), k -> new ArrayList<>()).add(r);
        }
        return agrupacion;
    }

    /**
     * Recorre cada grupo y asegura que todos los billetes tengan localizador único.
     * En caso de grupos coincidentes (>1 pasajero), genera identificadores inequívocos persistentes.
     */
    private void resolverLocalizadores(Map<String, List<Reserva>> agrupacionCoincidentes,
                                       Set<String> localizadoresExistentes) {
        for (Map.Entry<String, List<Reserva>> entry : agrupacionCoincidentes.entrySet()) {
            List<Reserva> grupo = entry.getValue();
            boolean esGrupoCoincidente = grupo.size() > 1;

            for (int subIdx = 0; subIdx < grupo.size(); subIdx++) {
                Reserva r = grupo.get(subIdx);
                asignarLocalizadorValido(r, subIdx + 1, esGrupoCoincidente, localizadoresExistentes);
            }
        }
    }

    /**
     * Valida y asigna un localizador no repetido a la reserva.
     */
    private void asignarLocalizadorValido(Reserva r, int indiceGrupo, boolean esGrupoCoincidente,
                                          Set<String> localizadoresExistentes) {
        String loc = r.getLocalizador();
        if (loc == null || loc.isBlank() || localizadoresExistentes.contains(loc)) {
            String nuevoLoc = generarLocalizadorUnico(r, indiceGrupo, esGrupoCoincidente);
            int intentos = 1;
            while (localizadoresExistentes.contains(nuevoLoc)) {
                nuevoLoc = generarLocalizadorUnico(r, indiceGrupo + intentos * 100, esGrupoCoincidente);
                intentos++;
            }
            r.setLocalizador(nuevoLoc);
        }
        localizadoresExistentes.add(r.getLocalizador());
    }

    /**
     * Genera un localizador legible con formato:
     * AT-[PAIS]-C[INDICE][LETRA] para colisiones, o AT-[PAIS]-[HASH][LETRA] para regulares.
     */
    private String generarLocalizadorUnico(Reserva r, int indice, boolean esCoincidente) {
        String codigoPais = r.getCodigoPaisDestino() != null ? r.getCodigoPaisDestino() : PAIS_DESCONOCIDO;
        char letraSecuencial = (char) ('A' + (indice % 26));
        String sufijo = esCoincidente
                ? "-C" + indice
                : "-" + UUID.randomUUID().toString().substring(0, 3).toUpperCase();

        return PREFIJO_LOCALIZADOR + codigoPais + sufijo + letraSecuencial;
    }

    /**
     * Extrae y analiza los grupos de reservas que coinciden en Nombre, Origen y Destino.
     * Retorna únicamente los grupos con 2 o más pasajeros coincidentes, ordenados de forma descendente.
     *
     * @param reservas Lista de reservas a analizar.
     * @return Lista de grupos de coincidencia encontrados.
     */
    public List<GrupoCoincidenciaDto> analizarCoincidencias(List<Reserva> reservas) {
        if (reservas == null || reservas.isEmpty()) {
            return Collections.emptyList();
        }

        // Reutiliza la misma lógica de agrupación por clave compuesta
        Map<String, List<Reserva>> agrupados = agruparPorCoincidencia(reservas);
        List<GrupoCoincidenciaDto> coincidencias = new ArrayList<>();

        for (Map.Entry<String, List<Reserva>> entry : agrupados.entrySet()) {
            List<Reserva> grupo = entry.getValue();
            if (grupo.size() > 1) { // 2 o más pasajeros coincidentes
                Reserva muestra = grupo.getFirst();
                coincidencias.add(new GrupoCoincidenciaDto(
                        entry.getKey(),
                        muestra.getNombrePasajero(),
                        muestra.getOrigen(),
                        muestra.getDestino(),
                        muestra.getCodigoPaisDestino(),
                        grupo.size(),
                        grupo
                ));
            }
        }

        // Ordenar con el grupo de más coincidentes primero de forma declarativa
        coincidencias.sort(Comparator.comparingInt(GrupoCoincidenciaDto::getTotalCoincidentes).reversed());
        return coincidencias;
    }
}
