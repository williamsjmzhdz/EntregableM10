package com.bazarboost.repository;

import com.bazarboost.model.Descuento;
import com.bazarboost.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/*
 * Autor: Francisco Williams Jiménez Hernández
 * Proyecto: Bazarboost
 * */
public interface DescuentoRepository extends JpaRepository<Descuento, Integer> {

    /**
     * Obtiene todos los descuentos asociados a un usuario específico.
     *
     * @param usuario Usuario cuyos descuentos se desean obtener.
     * @return Lista de descuentos asociados al usuario.
     */
    List<Descuento> findByUsuario(Usuario usuario);

    /**
     * Encuentra un descuento por su ID y el ID del usuario al que pertenece.
     *
     * @param descuentoId El ID del descuento.
     * @param usuarioId El ID del usuario dueño del descuento.
     * @return Un Optional con el descuento encontrado o vacío si no coincide.
     */
    Optional<Descuento> findByDescuentoIdAndUsuarioUsuarioId(Integer descuentoId, Integer usuarioId);

    /**
     * Busca un descuento por su nombre y el ID del usuario al que pertenece.
     * Utilizado para validar la unicidad del nombre de descuento por usuario.
     *
     * @param nombre El nombre del descuento a buscar.
     * @param usuarioId El ID del usuario dueño del descuento.
     * @return Un Optional con el descuento encontrado o vacío si no existe.
     */
    Optional<Descuento> findByNombreAndUsuarioUsuarioId(String nombre, Integer usuarioId);
}