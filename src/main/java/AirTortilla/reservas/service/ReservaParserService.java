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

/**
 * ¿QUÉ HACE ESTA CLASE? (Explicación para principiantes)
 * 
 * Esta clase se encarga de:
 * 
 * 1. LEER ARCHIVOS CSV:
 *    Toma el archivo CSV que subió el usuario y lo convierte en una lista de
 *    objetos Java "Reserva", usando la librería OpenCSV (CsvToBean).
 * 
 * 2. VALIDAR REGLAS DE NEGOCIO (Evitar conflictos):
 *    Comprueba fila por fila que los datos tengan sentido:
 *    - Regla 1: Que no falten datos obligatorios (nombre, origen o destino).
 *    - Regla 2: Que no sea una reserva repetida idéntica.
 *    - Regla 3: Que un pasajero no tenga dos vuelos en el mismo día.
 *    - Regla 4: Que no haya dos personas en el mismo asiento en el mismo vuelo y día.
 *    - Regla 5: Que el localizador no esté ya en uso por otra reserva.
 * 
 * 3. ASIGNAR IDENTIFICADORES Y LOCALIZADORES ÚNICOS:
 *    - Si una fila no tiene ID o su ID choca con otro existente en la BD, genera uno nuevo.
 *    - Si una fila no tiene localizador, genera uno único (y si hay pasajeros que coinciden
 *      en nombre y ruta, les genera localizadores diferenciados como C1A, C2B, etc.).
 */
@Service
public class ReservaParserService {

    private static final Logger log = LoggerFactory.getLogger(ReservaParserService.class);

    public static final String PAIS_DESCONOCIDO = "XX";
    private static final String PREFIJO_ID_RESERVA = "RES-";
    private static final String PREFIJO_LOCALIZADOR = "AT-";

    /**
     * Resultado estructurado del proceso de parseo:
     * - reservas: Las reservas válidas listas para guardarse.
     * - idsReasignados: Cuántos IDs se tuvieron que cambiar por estar repetidos.
     * - filasOmitidas: Cuántas filas se descartaron por colisión.
     * - detallesOmitidas: Los motivos por los que se omitieron filas.
     */
    public record ResultadoParseo(
            List<Reserva> reservas,
            int idsReasignados,
            int filasOmitidas,
            List<String> detallesOmitidas) {
    }

    // SECCIÓN 1

    /**
     * Parsea un flujo CSV a una lista de objetos Reserva.
     */
    public List<Reserva> parsearCsv(InputStream inputStream) {
        return parsearYValidarContraExistentes(inputStream, Collections.emptyList());
    }

    /**
     * Parsea un flujo CSV validando las filas contra las que ya están en la base de datos.
     */
    public List<Reserva> parsearYValidarContraExistentes(InputStream inputStream, List<Reserva> existentesEnBd) {
        return parsearConResumen(inputStream, existentesEnBd).reservas();
    }

    /**
     * Parsea un flujo CSV devolviendo el resumen completo (válidas, omitidas, reasignadas).
     */
    public ResultadoParseo parsearConResumen(InputStream inputStream, List<Reserva> existentesEnBd) {
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parsearDesdeReader(reader, existentesEnBd);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al leer el flujo CSV", e);
            throw new IllegalArgumentException("No se pudo procesar el archivo CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Parsea directamente un texto CSV en formato String a una lista de reservas.
     */
    public List<Reserva> parsearCsvTexto(String contenidoCsv) {
        try (Reader reader = new StringReader(contenidoCsv)) {
            return parsearDesdeReader(reader, Collections.emptyList()).reservas();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al parsear el contenido CSV", e);
            throw new IllegalArgumentException("Error al parsear el contenido CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Normaliza los campos de una lista de reservas y asegura sus identificadores.
     */
    public void normalizarYAsegurarIdentificadores(List<Reserva> reservas) {
        validarYPrepararReservas(reservas, Collections.emptyList());
    }

    /**
     * Valida y prepara una lista de reservas en memoria contra las ya existentes en BD.
     */
    public void validarYPrepararReservas(List<Reserva> reservas, List<Reserva> existentesEnBd) {
        if (reservas == null || reservas.isEmpty()) {
            return;
        }
        ResultadoParseo resultado = filtrarYPrepararReservas(reservas, existentesEnBd);
        reservas.clear();
        reservas.addAll(resultado.reservas());
    }

    // SECCIÓN 2

    private ResultadoParseo parsearDesdeReader(Reader reader, List<Reserva> existentesEnBd) {
        // Configuramos OpenCSV para mapear las columnas del CSV a los atributos de Reserva
        HeaderColumnNameMappingStrategy<Reserva> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(Reserva.class);

        CsvToBean<Reserva> csvToBean = new CsvToBeanBuilder<Reserva>(reader)
                .withMappingStrategy(strategy)
                .withIgnoreLeadingWhiteSpace(true)
                .withIgnoreEmptyLine(true)
                .withThrowExceptions(false)
                .build();

        List<Reserva> reservasLeidas = csvToBean.parse();

        // Filtramos conflictos y aseguramos identificadores únicos
        ResultadoParseo resultado = filtrarYPrepararReservas(reservasLeidas, existentesEnBd);

        log.info("Parseadas {} reservas válidas con OpenCSV ({} omitidas por conflicto, {} IDs reasignados)",
                resultado.reservas().size(), resultado.filasOmitidas(), resultado.idsReasignados());

        return resultado;
    }

    // SECCIÓN 3

    /**
     * Estructura auxiliar para comprobar rápidamente si un dato ya existe sin hacer
     * consultas repetidas a la base de datos.
     */
    private static class IndiceConflictos {
        final Map<String, String> reservasExactas = new HashMap<>();       // Clave exacta -> Dónde se encontró
        final Map<String, String> pasajeroPorDia = new HashMap<>();        // Pasajero + Día -> Dónde se encontró
        final Map<String, String> vueloAsiento = new HashMap<>();          // Vuelo + Asiento -> Dónde se encontró
        final Map<String, String> localizadores = new HashMap<>();         // Localizador -> Dónde se encontró

        void registrar(Reserva r, String origen) {
            reservasExactas.putIfAbsent(crearClaveExacta(r), origen);
            pasajeroPorDia.putIfAbsent(crearClavePasajeroDia(r), origen);

            if (r.getAsiento() != null && !r.getAsiento().isBlank()) {
                vueloAsiento.putIfAbsent(crearClaveVueloAsiento(r), origen);
            }
            if (r.getLocalizador() != null && !r.getLocalizador().isBlank()) {
                localizadores.putIfAbsent(r.getLocalizador().trim().toUpperCase(Locale.ROOT), origen);
            }
        }
    }

    /**
     * Procesa las reservas leídas: descarta las que tengan conflictos de negocio,
     * reasigna IDs duplicados y genera localizadores si faltan.
     */
    public ResultadoParseo filtrarYPrepararReservas(List<Reserva> candidatas, List<Reserva> existentesEnBd) {
        if (candidatas == null || candidatas.isEmpty()) {
            return new ResultadoParseo(Collections.emptyList(), 0, 0, Collections.emptyList());
        }

        List<Reserva> existentes = (existentesEnBd != null) ? existentesEnBd : Collections.emptyList();
        List<String> detallesOmitidas = new ArrayList<>();

        // PASO 1: Filtrar las reservas que entran en conflicto con la BD o entre ellas mismas
        List<Reserva> validas = filtrarReservasSinConflictos(candidatas, existentes, detallesOmitidas);

        // PASO 2: Recopilar los IDs y localizadores que ya están ocupados en la BD
        Set<String> idsOcupados = new HashSet<>();
        Set<String> localizadoresOcupados = new HashSet<>();

        for (Reserva ex : existentes) {
            if (ex.getId() != null && !ex.getId().isBlank()) {
                idsOcupados.add(ex.getId().trim().toUpperCase(Locale.ROOT));
            }
            if (ex.getLocalizador() != null && !ex.getLocalizador().isBlank()) {
                localizadoresOcupados.add(ex.getLocalizador().trim().toUpperCase(Locale.ROOT));
            }
        }
        for (Reserva val : validas) {
            if (val.getLocalizador() != null && !val.getLocalizador().isBlank()) {
                localizadoresOcupados.add(val.getLocalizador().trim().toUpperCase(Locale.ROOT));
            }
        }

        // PASO 3: Asegurar que cada reserva válida tenga un ID único
        int secuenciaBase = existentes.size();
        int idsReasignados = asegurarIdsUnicos(validas, idsOcupados, secuenciaBase);

        // PASO 4: Asegurar que cada reserva válida tenga un localizador único
        asegurarLocalizadoresUnicos(validas, existentes, localizadoresOcupados);

        return new ResultadoParseo(validas, idsReasignados, detallesOmitidas.size(), List.copyOf(detallesOmitidas));
    }

    /**
     * Revisa una por una las reservas leídas del archivo. Si alguna incumple
     * una regla de negocio, se anota el motivo y se descarta.
     */
    private List<Reserva> filtrarReservasSinConflictos(List<Reserva> candidatas,
                                                      List<Reserva> existentes,
                                                      List<String> detallesOmitidas) {
        IndiceConflictos indice = new IndiceConflictos();

        // Guardamos en el índice lo que ya existe en la Base de Datos
        for (Reserva ex : existentes) {
            indice.registrar(ex, "en la base de datos");
        }

        List<Reserva> validas = new ArrayList<>();

        // Revisamos cada fila del archivo CSV
        for (int i = 0; i < candidatas.size(); i++) {
            Reserva r = candidatas.get(i);
            int numeroFila = i + 1;
            String origenFila = "fila " + numeroFila + " del archivo";

            // Normalizamos campos básicos (mayúsculas, país XX si falta, fecha actual si falta)
            normalizarCamposBasicos(r);

            // Verificamos si esta fila tiene algún conflicto
            String motivoConflicto = detectarConflicto(r, numeroFila, indice);

            if (motivoConflicto != null) {
                log.warn("Reserva omitida ({}): {}", origenFila, motivoConflicto);
                detallesOmitidas.add(motivoConflicto);
                continue; // Saltamos esta fila conflictiva
            }

            // Si es válida, la guardamos y la registramos en el índice para filas posteriores
            validas.add(r);
            indice.registrar(r, origenFila);
        }

        return validas;
    }

    /**
     * Aplica las 5 reglas de negocio para saber si una reserva entra en conflicto.
     * Retorna el mensaje de error si hay conflicto, o null si es válida.
     */
    private String detectarConflicto(Reserva r, int numeroFila, IndiceConflictos indice) {
        // Regla 1: Campos obligatorios
        if (estaVacio(r.getNombrePasajero()) || estaVacio(r.getOrigen()) || estaVacio(r.getDestino())) {
            return String.format("Fila %d: faltan campos obligatorios (nombre_pasajero, origen o destino).", numeroFila);
        }

        // Regla 2: Reserva duplicada exacta (coincide en todos los datos)
        String claveExacta = crearClaveExacta(r);
        if (indice.reservasExactas.containsKey(claveExacta)) {
            String origen = indice.reservasExactas.get(claveExacta);
            return String.format(
                    "Fila %d: reserva duplicada, coincide en todos los campos con una reserva existente (%s) para '%s' (%s -> %s, %s).",
                    numeroFila, origen, r.getNombrePasajero(), r.getOrigen(), r.getDestino(), r.getFechaReserva());
        }

        // Regla 3: Mismo pasajero volando dos veces en el mismo día
        String clavePaxDia = crearClavePasajeroDia(r);
        if (indice.pasajeroPorDia.containsKey(clavePaxDia)) {
            String origen = indice.pasajeroPorDia.get(clavePaxDia);
            return String.format(
                    "Fila %d: el pasajero '%s' ya tiene un vuelo registrado el día %s (%s). No puede volar dos veces el mismo día.",
                    numeroFila, r.getNombrePasajero(), r.getFechaReserva(), origen);
        }

        // Regla 4: Mismo asiento ocupado en el mismo vuelo y día
        if (!estaVacio(r.getAsiento())) {
            String claveAsiento = crearClaveVueloAsiento(r);
            if (indice.vueloAsiento.containsKey(claveAsiento)) {
                String origen = indice.vueloAsiento.get(claveAsiento);
                return String.format(
                        "Fila %d: el asiento '%s' del vuelo %s -> %s (%s) ya está ocupado (%s).",
                        numeroFila, r.getAsiento(), r.getOrigen(), r.getDestino(), r.getFechaReserva(), origen);
            }
        }

        // Regla 5: Localizador repetido
        if (!estaVacio(r.getLocalizador())) {
            String claveLoc = r.getLocalizador().trim().toUpperCase(Locale.ROOT);
            if (indice.localizadores.containsKey(claveLoc)) {
                String origen = indice.localizadores.get(claveLoc);
                return String.format(
                        "Fila %d: el localizador '%s' ya existe (%s). No puede reutilizarse en otra reserva.",
                        numeroFila, r.getLocalizador(), origen);
            }
        }

        return null; // Todo en orden
    }

    // SECCIÓN 4

    /**
     * Revisa las reservas válidas: si alguna no tiene ID o su ID choca con uno existente
     * en la BD o en filas anteriores, genera uno nuevo con formato RES-XX-0001-ABCD.
     */
    private int asegurarIdsUnicos(List<Reserva> validas, Set<String> idsOcupados, int inicioSecuencia) {
        int idsReasignados = 0;

        for (int i = 0; i < validas.size(); i++) {
            Reserva r = validas.get(i);
            String idActual = r.getId();

            boolean yaExiste = idActual != null && !idActual.isBlank()
                    && idsOcupados.contains(idActual.trim().toUpperCase(Locale.ROOT));
            boolean estaVacio = idActual == null || idActual.isBlank();

            if (estaVacio || yaExiste) {
                // Generar un ID único que no esté en idsOcupados
                String nuevoId;
                int secuencia = inicioSecuencia + i + 1;
                do {
                    String sufijo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                    nuevoId = String.format("%s%s-%04d-%s", PREFIJO_ID_RESERVA, r.getCodigoPaisDestino(), secuencia, sufijo);
                } while (idsOcupados.contains(nuevoId.toUpperCase(Locale.ROOT)));

                r.setId(nuevoId);

                if (yaExiste) {
                    idsReasignados++;
                    log.info("ID '{}' ya existente en BD/lote para '{}'; reasignado automáticamente a '{}'",
                            idActual, r.getNombrePasajero(), nuevoId);
                }
            }

            idsOcupados.add(r.getId().toUpperCase(Locale.ROOT));
        }

        return idsReasignados;
    }

    /**
     * Asegura que todas las reservas tengan un localizador único:
     * Si no traían localizador en el archivo CSV, les genera uno único.
     * Si además hay varios pasajeros con el mismo nombre y ruta, les genera
     * localizadores diferenciados (ej: AT-ES-C1A, AT-ES-C2B).
     */
    private void asegurarLocalizadoresUnicos(List<Reserva> validas,
                                             List<Reserva> existentes,
                                             Set<String> localizadoresOcupados) {
        // Agrupamos todas las reservas (existentes + válidas) por Pasajero + Origen + Destino
        List<Reserva> todas = new ArrayList<>(existentes.size() + validas.size());
        todas.addAll(existentes);
        todas.addAll(validas);

        Map<String, List<Reserva>> agrupados = agruparPorCoincidencia(todas);

        // Guardamos los IDs de las reservas válidas de este lote para saber a cuáles asignar
        Set<String> idsValidas = new HashSet<>();
        for (Reserva r : validas) {
            idsValidas.add(r.getId());
        }

        for (List<Reserva> grupo : agrupados.values()) {
            boolean esGrupoCoincidente = grupo.size() > 1;

            for (int subIdx = 0; subIdx < grupo.size(); subIdx++) {
                Reserva r = grupo.get(subIdx);

                if (idsValidas.contains(r.getId())) {
                    if (estaVacio(r.getLocalizador())) {
                        String nuevoLoc = generarLocalizadorValido(r, subIdx + 1, esGrupoCoincidente, localizadoresOcupados);
                        r.setLocalizador(nuevoLoc);
                    }
                    localizadoresOcupados.add(r.getLocalizador().trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    private String generarLocalizadorValido(Reserva r, int indiceGrupo, boolean esCoincidente, Set<String> ocupados) {
        String codigoPais = (r.getCodigoPaisDestino() != null) ? r.getCodigoPaisDestino() : PAIS_DESCONOCIDO;
        int intento = 0;
        String localizador;

        do {
            int indiceAjustado = indiceGrupo + (intento * 100);
            char letra = (char) ('A' + (indiceAjustado % 26));
            String sufijo = esCoincidente
                    ? "-C" + indiceAjustado
                    : "-" + UUID.randomUUID().toString().substring(0, 3).toUpperCase();

            localizador = PREFIJO_LOCALIZADOR + codigoPais + sufijo + letra;
            intento++;
        } while (ocupados.contains(localizador.toUpperCase(Locale.ROOT)));

        return localizador;
    }

    // SECCIÓN 5

    /**
     * Busca grupos de reservas donde 2 o más pasajeros coinciden en Nombre, Origen y Destino.
     * Retorna la lista ordenada de mayor a menor número de pasajeros coincidentes.
     */
    public List<GrupoCoincidenciaDto> analizarCoincidencias(List<Reserva> reservas) {
        if (reservas == null || reservas.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Reserva>> agrupados = agruparPorCoincidencia(reservas);
        List<GrupoCoincidenciaDto> coincidencias = new ArrayList<>();

        for (Map.Entry<String, List<Reserva>> entry : agrupados.entrySet()) {
            List<Reserva> grupo = entry.getValue();

            // Solo incluimos grupos con 2 o más reservas
            if (grupo.size() > 1) {
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

        // Ordenar con el grupo más grande primero
        coincidencias.sort(Comparator.comparingInt(GrupoCoincidenciaDto::getTotalCoincidentes).reversed());
        return coincidencias;
    }

    private Map<String, List<Reserva>> agruparPorCoincidencia(List<Reserva> reservas) {
        Map<String, List<Reserva>> agrupacion = new LinkedHashMap<>();
        for (Reserva r : reservas) {
            agrupacion.computeIfAbsent(r.getClaveCoincidencia(), k -> new ArrayList<>()).add(r);
        }
        return agrupacion;
    }

    // MÉTODOS AUXILIARES Y NORMALIZACIÓN

    private static void normalizarCamposBasicos(Reserva r) {
        String pais = r.getCodigoPaisDestino();
        r.setCodigoPaisDestino((pais != null && !pais.isBlank()) ? pais.trim().toUpperCase() : PAIS_DESCONOCIDO);

        if (r.getFechaReserva() == null) {
            r.setFechaReserva(LocalDate.now());
        }
    }

    private static String crearClaveExacta(Reserva r) {
        String precioStr = (r.getPrecio() != null) ? String.format(Locale.US, "%.2f", r.getPrecio()) : "";
        return String.join("||",
                normalizarTexto(r.getNombrePasajero()),
                normalizarTexto(r.getOrigen()),
                normalizarTexto(r.getDestino()),
                normalizarTexto(r.getCodigoPaisDestino()),
                Objects.toString(r.getFechaReserva(), ""),
                precioStr,
                normalizarTexto(r.getAsiento())
        );
    }

    private static String crearClavePasajeroDia(Reserva r) {
        return normalizarTexto(r.getNombrePasajero()) + "||" + Objects.toString(r.getFechaReserva(), "");
    }

    private static String crearClaveVueloAsiento(Reserva r) {
        return String.join("||",
                normalizarTexto(r.getOrigen()),
                normalizarTexto(r.getDestino()),
                Objects.toString(r.getFechaReserva(), ""),
                normalizarTexto(r.getAsiento())
        );
    }

    private static String normalizarTexto(String s) {
        return (s != null) ? s.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static boolean estaVacio(String s) {
        return (s == null || s.isBlank());
    }
}
