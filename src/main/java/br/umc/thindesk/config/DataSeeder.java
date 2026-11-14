package br.umc.thindesk.config;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.repository.Contador;
import br.umc.thindesk.seed.GeradorDados;

/** Na primeira execução (coleção vazia), popula o MongoDB Atlas com dados de exemplo. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final MongoTemplate mongo;
    private final Clock clock;
    private final boolean habilitado;

    public DataSeeder(MongoTemplate mongo, Clock clock, @Value("${thindesk.seed.enabled:true}") boolean habilitado) {
        this.mongo = mongo;
        this.clock = clock;
        this.habilitado = habilitado;
    }

    @Override
    public void run(String... args) {
        if (!habilitado) return;
        if (mongo.count(new Query(), Chamado.class) > 0) {
            log.info("Banco já possui chamados; seed ignorado.");
            return;
        }
        var dados = new GeradorDados(42).gerar(LocalDateTime.now(clock), 730);
        mongo.insert(dados.categorias(), Categoria.class);
        mongo.insert(dados.tecnicos(), Tecnico.class);
        mongo.insert(dados.chamados(), Chamado.class);
        mongo.upsert(Query.query(Criteria.where("_id").is("chamados")),
                new Update().set("valor", (long) dados.chamados().size()), Contador.class);
        log.info("Seed concluído: {} categorias, {} técnicos, {} chamados.",
                dados.categorias().size(), dados.tecnicos().size(), dados.chamados().size());
    }
}
