package br.umc.thindesk.support;

import java.util.List;
import java.util.Optional;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.repository.CatalogoGateway;

public class InMemoryCatalogoGateway implements CatalogoGateway {
    private final List<Categoria> categorias;
    private final List<Tecnico> tecnicos;

    public InMemoryCatalogoGateway(List<Categoria> categorias, List<Tecnico> tecnicos) {
        this.categorias = categorias;
        this.tecnicos = tecnicos;
    }

    @Override public List<Categoria> categorias() { return categorias; }
    @Override public List<Tecnico> tecnicos() { return tecnicos; }

    @Override
    public Optional<Categoria> categoria(String id) {
        return categorias.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Tecnico> tecnico(String id) {
        return tecnicos.stream().filter(t -> t.getId().equals(id)).findFirst();
    }
}
