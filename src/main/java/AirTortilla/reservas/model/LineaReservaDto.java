package AirTortilla.reservas.model;

/**
 * DTO para la consulta en modalidad "Línea a Línea".
 */
public class LineaReservaDto {
    private String nombreArchivo;
    private int numeroLinea;       // Índice de línea de datos (1 a N)
    private int totalLineas;       // Total de líneas de datos en el archivo
    private String lineaRaw;       // Texto en bruto de la línea CSV
    private Reserva reserva;       // Objeto de reserva parseado
    private boolean tieneSiguiente;
    private boolean tieneAnterior;
    private boolean esCoincidente; // Si comparte nombre, origen y destino con otra reserva
    private int totalCoincidentes; // Cuántas reservas comparten esos datos

    public LineaReservaDto() {
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public int getNumeroLinea() {
        return numeroLinea;
    }

    public void setNumeroLinea(int numeroLinea) {
        this.numeroLinea = numeroLinea;
    }

    public int getTotalLineas() {
        return totalLineas;
    }

    public void setTotalLineas(int totalLineas) {
        this.totalLineas = totalLineas;
    }

    public String getLineaRaw() {
        return lineaRaw;
    }

    public void setLineaRaw(String lineaRaw) {
        this.lineaRaw = lineaRaw;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public void setReserva(Reserva reserva) {
        this.reserva = reserva;
    }

    public boolean isTieneSiguiente() {
        return tieneSiguiente;
    }

    public void setTieneSiguiente(boolean tieneSiguiente) {
        this.tieneSiguiente = tieneSiguiente;
    }

    public boolean isTieneAnterior() {
        return tieneAnterior;
    }

    public void setTieneAnterior(boolean tieneAnterior) {
        this.tieneAnterior = tieneAnterior;
    }

    public boolean isEsCoincidente() {
        return esCoincidente;
    }

    public void setEsCoincidente(boolean esCoincidente) {
        this.esCoincidente = esCoincidente;
    }

    public int getTotalCoincidentes() {
        return totalCoincidentes;
    }

    public void setTotalCoincidentes(int totalCoincidentes) {
        this.totalCoincidentes = totalCoincidentes;
    }
}
