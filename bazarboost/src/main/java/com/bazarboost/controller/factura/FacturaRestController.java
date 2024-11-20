package com.bazarboost.controller.factura;

import com.bazarboost.dto.CarritoPagoRespuestaDTO;
import com.bazarboost.dto.CarritoPagoSolicitudDTO;
import com.bazarboost.dto.FacturasPaginadasDTO;
import com.bazarboost.service.FacturaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    private static final Integer USUARIO_ID_TEMPORAL = 1;
    private static final Integer TAMANO_PAGINA = 10;

    @Autowired
    private FacturaService facturaService;

    @PostMapping
    public ResponseEntity<CarritoPagoRespuestaDTO> procesarPago(
            @RequestBody @Valid CarritoPagoSolicitudDTO carritoPagoSolicitudDTO
    ) {
        CarritoPagoRespuestaDTO carritoPagoRespuestaDTO = facturaService.procesarPago(carritoPagoSolicitudDTO, USUARIO_ID_TEMPORAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(carritoPagoRespuestaDTO);
    }

    @GetMapping
    public ResponseEntity<FacturasPaginadasDTO> listarFacturas(
            @RequestParam(defaultValue = "fecha") String ordenarPor,
            @RequestParam(defaultValue = "desc") String direccionOrden,
            @RequestParam(defaultValue = "0") Integer pagina
    ) {
        FacturasPaginadasDTO facturas = facturaService.obtenerFacturasPaginadasYOrdenadas(ordenarPor, direccionOrden, pagina, TAMANO_PAGINA, USUARIO_ID_TEMPORAL);
        return ResponseEntity.ok(facturas);
    }

}
