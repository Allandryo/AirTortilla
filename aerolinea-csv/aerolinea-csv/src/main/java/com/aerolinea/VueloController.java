package com.aerolinea;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vuelos")
@CrossOrigin(origins = "*")
public class VueloController {

    private final VueloService vueloService;

    public VueloController(VueloService vueloService) {
        this.vueloService = vueloService;
    }

    @GetMapping("/pasajeros")
    public List<ResumenPasajero> obtenerPasajeros(
            @RequestParam(required = false) String pasajero,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String destino,
            @RequestParam(required = false) String clase) {
        return vueloService.obtenerPasajerosConVuelos(pasajero, origen, destino, clase);
    }

    @GetMapping("/todos")
    public List<ReservaVueloCsv> obtenerTodosLosVuelos() {
        return vueloService.obtenerTodosLosVuelos();
    }
}
