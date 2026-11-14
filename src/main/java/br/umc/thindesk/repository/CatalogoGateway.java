package br.umc.thindesk.repository;

import java.util.List;
import java.util.Optional;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Tecnico;

public interface CatalogoGateway {
    List<Categoria> categorias();

    List<Tecnico> tecnicos();

    Optional<Categoria> categoria(String id);

    Optional<Tecnico> tecnico(String id);
}
