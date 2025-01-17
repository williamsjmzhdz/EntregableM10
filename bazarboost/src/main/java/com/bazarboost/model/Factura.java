package com.bazarboost.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/*
* Alumno: Francisco Williams Jiménez Hernández
* Proyecto: Bazarboost
* */

@Entity
@Table(name = "Facturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "factura_id")
    private Integer facturaId;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "total")
    private Double total;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "metodo_pago_id")
    private MetodoPago metodoPago;

    @ManyToOne
    @JoinColumn(name = "direccion_id")
    private Direccion direccion;
}
