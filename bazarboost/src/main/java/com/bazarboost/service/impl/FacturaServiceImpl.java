package com.bazarboost.service.impl;

import com.bazarboost.dto.*;
import com.bazarboost.exception.*;
import com.bazarboost.model.*;
import com.bazarboost.repository.*;
import com.bazarboost.service.FacturaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FacturaServiceImpl implements FacturaService {

    @Autowired
    private FacturaRepository facturaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProductoCarritoRepository productoCarritoRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private MetodoPagoRepository metodoPagoRepository;
    @Autowired
    private DireccionRepository direccionRepository;
    @Autowired
    private ProductoFacturaRepository productoFacturaRepository;

    @Override
    @Transactional
    public CarritoPagoRespuestaDTO procesarPago(CarritoPagoSolicitudDTO solicitudDTO, Integer usuarioId) {

        if (solicitudDTO.getProductos() == null || solicitudDTO.getProductos().isEmpty()) {
            throw new CarritoVacioException("El carrito de compras está vacío. Agrega productos antes de proceder al pago.");
        }

        Usuario usuario = obtenerUsuario(usuarioId);
        BigDecimal precioTotal = calcularPrecioTotalYVerificarCantidades(solicitudDTO, usuarioId);
        MetodoPago metodoPago = obtenerMetodoPago(solicitudDTO.getMetodoPagoId(), usuario);
        Direccion direccion = obtenerDireccion(solicitudDTO.getDireccionId(), usuario);

        actualizarStockProductos(solicitudDTO.getProductos());
        descontarMontoMetodoPago(metodoPago, precioTotal);

        Factura factura = crearFactura(precioTotal, usuario, metodoPago, direccion);
        guardarDetallesFactura(factura, solicitudDTO.getProductos());

        vaciarCarrito(usuarioId);

        return new CarritoPagoRespuestaDTO(factura.getFacturaId());
    }

    @Override
    @Transactional(readOnly = true)
    public FacturasPaginadasDTO obtenerFacturasPaginadasYOrdenadas(String ordenarPor, String direccionOrden, Integer pagina, Integer tamanoPagina,
                                                               Integer usuarioId) {
        Usuario usuario = obtenerUsuario(usuarioId);  // Verificación de que el usuario existe

        if (!ordenarPor.equalsIgnoreCase("fecha") && !ordenarPor.equalsIgnoreCase("total")) {
            throw new OrdenNoValidoException("Parámetro de ordenación no válido. Use 'fecha' o 'total'.");
        }

        if (!direccionOrden.equalsIgnoreCase("asc") && !direccionOrden.equalsIgnoreCase("desc")) {
            throw new OrdenNoValidoException("Dirección de orden no válida. Use 'asc' o 'desc'.");
        }

        long totalFacturas = facturaRepository.countByUsuario(usuario);

        // Si no hay facturas, devolvemos una respuesta vacía sin lanzar excepción
        if (totalFacturas == 0) {
            return new FacturasPaginadasDTO(
                    List.of(),   // Lista vacía de facturas
                    pagina,      // Página solicitada, aunque vacía
                    0,           // Total de páginas es 0
                    0,           // Total de elementos es 0
                    true,        // Es la primera página (y también la última)
                    true         // Es la última página (no hay más páginas)
            );
        }

        int maximoPaginas = (int) Math.ceil((double) totalFacturas / tamanoPagina);

        if (pagina < 0 || pagina >= maximoPaginas) {
            throw new PaginaFueraDeRangoException("Número de página fuera de rango. Total de páginas disponibles: " + maximoPaginas);
        }

        Sort sort = direccionOrden.equals("asc") ? Sort.by(ordenarPor).ascending() : Sort.by(ordenarPor).descending();
        Pageable pageable = PageRequest.of(pagina, tamanoPagina, sort);

        Page<Factura> facturasPage = facturaRepository.findByUsuario(usuario, pageable);

        List<FacturaDTO> facturasDTO = facturasPage.getContent().stream()
                .map(factura -> new FacturaDTO(factura.getFacturaId(), factura.getFecha(), factura.getTotal()))
                .toList();

        return new FacturasPaginadasDTO(
                facturasDTO,
                facturasPage.getNumber(),
                facturasPage.getTotalPages(),
                facturasPage.getTotalElements(),
                facturasPage.isFirst(),
                facturasPage.isLast()
        );

    }

    private BigDecimal calcularPrecioTotalYVerificarCantidades(CarritoPagoSolicitudDTO solicitudDTO, Integer usuarioId) {
        AtomicReference<BigDecimal> precioTotal = new AtomicReference<>(BigDecimal.ZERO);

        solicitudDTO.getProductos().forEach(productoPagoDTO -> {
            Producto producto = obtenerProducto(productoPagoDTO.getProductoId());
            Integer cantidadEnCarrito = obtenerCantidadProductoEnCarrito(usuarioId, producto.getProductoId());

            if (!cantidadEnCarrito.equals(productoPagoDTO.getCantidad())) {
                throw new CantidadNoCoincideException(String.format(
                        "La cantidad enviada (%d) para el producto '%s' no coincide con la cantidad en el carrito (%d).",
                        productoPagoDTO.getCantidad(), producto.getNombre(), cantidadEnCarrito));
            }

            BigDecimal precioProductoConDescuento = calcularPrecioConDescuento(producto)
                    .multiply(BigDecimal.valueOf(productoPagoDTO.getCantidad()));
            precioTotal.updateAndGet(total -> total.add(precioProductoConDescuento));
        });

        return precioTotal.get();
    }

    private Integer obtenerCantidadProductoEnCarrito(Integer usuarioId, Integer productoId) {
        return productoCarritoRepository.obtenerCantidadProductoCarrito(usuarioId, productoId)
                .orElseThrow(() -> new ProductoNoEnCarritoException(
                        String.format("El producto con ID %d no está en el carrito del usuario con ID %d", productoId, usuarioId)));
    }

    private void actualizarStockProductos(List<ProductoPagoDTO> productosPagoDTO) {
        productosPagoDTO.forEach(this::actualizarStock);
    }

    private void actualizarStock(ProductoPagoDTO productoPagoDTO) {
        Producto producto = obtenerProducto(productoPagoDTO.getProductoId());
        int nuevaCantidad = producto.getExistencia() - productoPagoDTO.getCantidad();

        if (nuevaCantidad < 0) {
            throw new StockInsuficienteException("Stock insuficiente para el producto '" + producto.getNombre()
                    + "'. Disponible: " + producto.getExistencia());
        }

        productoRepository.actualizarStock(productoPagoDTO.getProductoId(), nuevaCantidad);
    }

    private void descontarMontoMetodoPago(MetodoPago metodoPago, BigDecimal precioTotal) {
        if (metodoPago.getMonto() == null || BigDecimal.valueOf(metodoPago.getMonto()).compareTo(precioTotal) < 0) {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

            String disponibleFormateado = currencyFormat.format(metodoPago.getMonto());
            String requeridoFormateado = currencyFormat.format(precioTotal);

            throw new FondosInsuficientesException("Fondos insuficientes en el método de pago. Disponible: "
                    + disponibleFormateado + ", Requerido: " + requeridoFormateado);
        }

        metodoPagoRepository.reducirMonto(metodoPago.getMetodoPagoId(), precioTotal.doubleValue());
    }

    private Factura crearFactura(BigDecimal precioTotal, Usuario usuario, MetodoPago metodoPago, Direccion direccion) {
        Factura factura = new Factura();
        factura.setDireccion(direccion);
        factura.setTotal(precioTotal.doubleValue());
        factura.setFecha(LocalDateTime.now());
        factura.setMetodoPago(metodoPago);
        factura.setUsuario(usuario);
        return facturaRepository.save(factura);
    }

    private void guardarDetallesFactura(Factura factura, List<ProductoPagoDTO> productosPagoDTO) {
        productosPagoDTO.forEach(productoPagoDTO -> {
            Producto producto = obtenerProducto(productoPagoDTO.getProductoId());
            ProductoFactura productoFactura = new ProductoFactura();

            productoFactura.setFactura(factura);
            productoFactura.setProducto(producto);
            productoFactura.setCantidad(productoPagoDTO.getCantidad());
            productoFactura.setTotal(calcularPrecioConDescuento(producto)
                    .multiply(BigDecimal.valueOf(productoPagoDTO.getCantidad()))
                    .doubleValue());

            productoFacturaRepository.save(productoFactura);
        });
    }

    private BigDecimal calcularPrecioConDescuento(Producto producto) {
        if (producto.getDescuento() != null) {
            return producto.getPrecio().subtract(calcularValorDescuento(producto));
        }
        return producto.getPrecio();
    }

    private BigDecimal calcularValorDescuento(Producto producto) {
        return producto.getPrecio()
                .multiply(BigDecimal.valueOf(producto.getDescuento().getPorcentaje()))
                .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
    }

    private void vaciarCarrito(Integer usuarioId) {
        productoCarritoRepository.deleteByUsuarioUsuarioId(usuarioId);
    }

    // Métodos auxiliares para obtener entidades

    private Producto obtenerProducto(Integer productoId) {
        return productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto con ID " + productoId + " no encontrado"));
    }

    private Usuario obtenerUsuario(Integer usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario con ID " + usuarioId + " no encontrado"));
    }

    private MetodoPago obtenerMetodoPago(Integer metodoPagoId, Usuario usuario) {
        return metodoPagoRepository.findByMetodoPagoIdAndUsuario(metodoPagoId, usuario)
                .orElseThrow(() -> new MetodoPagoNoEncontradoException(String.format(
                        "No se encontró un método de pago con ID %d para el usuario con ID %d", metodoPagoId, usuario.getUsuarioId())));
    }

    private Direccion obtenerDireccion(Integer direccionId, Usuario usuario) {
        return direccionRepository.findByDireccionIdAndUsuario(direccionId, usuario)
                .orElseThrow(() -> new DireccionNoEncontradaException(String.format(
                        "No se encontró una dirección con ID %d para el usuario con ID %d", direccionId, usuario.getUsuarioId())));
    }
}
