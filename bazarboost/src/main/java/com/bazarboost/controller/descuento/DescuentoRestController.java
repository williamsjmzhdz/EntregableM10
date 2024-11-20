package com.bazarboost.controller.descuento;

import com.bazarboost.dto.DescuentoVendedorDTO;
import com.bazarboost.model.Descuento;
import com.bazarboost.model.Usuario;
import com.bazarboost.service.DescuentoService;
import com.bazarboost.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/descuentos")
public class DescuentoRestController {

    private static final Integer VENDEDOR_ID_TEMPORAL = 1;

    @Autowired
    private DescuentoService descuentoService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/mis-descuentos")
    @ResponseBody
    private ResponseEntity<List<DescuentoVendedorDTO>> mostrarMisDescuentos() {
        return ResponseEntity.ok().body(descuentoService.obtenerDescuentosDTOPorUsuario(VENDEDOR_ID_TEMPORAL));
    }

    @PostMapping
    public ResponseEntity<Void> crearDescuento(@Valid @RequestBody Descuento descuento) {
        descuentoService.crearDescuento(descuento, VENDEDOR_ID_TEMPORAL);
        return new ResponseEntity<Void>(HttpStatus.CREATED);
    }

    @PutMapping("/{descuentoId}")
    public ResponseEntity<Void> actualizarDescuento(
            @PathVariable Integer descuentoId,
            @Valid @RequestBody Descuento descuentoActualizado) {
        descuentoService.actualizarDescuento(descuentoId, descuentoActualizado, VENDEDOR_ID_TEMPORAL);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{descuentoId}")
    public ResponseEntity<Void> eliminarDescuento(@PathVariable Integer descuentoId) {
        descuentoService.eliminarDescuento(descuentoId, VENDEDOR_ID_TEMPORAL);
        return ResponseEntity.noContent().build();
    }


}
