package br.umc.thindesk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Tecnico;

@Repository
public class MongoCatalogoGateway implements CatalogoGateway {

    private final MongoTemplate mongo;

    public MongoCatalogoGateway(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Categoria> categorias() {
        return mongo.find(new Query().with(Sort.by("nome")), Categoria.class);
    }

    @Override
    public List<Tecnico> tecnicos() {
        return mongo.find(new Query().with(Sort.by("nome")), Tecnico.class);
    }

    @Override
    public Optional<Categoria> categoria(String id) {
        return Optional.ofNullable(mongo.findById(id, Categoria.class));
    }

    @Override
    public Optional<Tecnico> tecnico(String id) {
        return Optional.ofNullable(mongo.findById(id, Tecnico.class));
    }
}
