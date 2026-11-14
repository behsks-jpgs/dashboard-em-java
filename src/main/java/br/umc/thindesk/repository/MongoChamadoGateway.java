package br.umc.thindesk.repository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.service.Filtros;

/** Implementação com MongoDB (Atlas) usando MongoTemplate + Criteria. */
@Repository
public class MongoChamadoGateway implements ChamadoGateway {

    private final MongoTemplate mongo;

    public MongoChamadoGateway(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Chamado> buscar(Filtros filtros, CampoData campoData) {
        return mongo.find(new Query(criteria(filtros, campoData)), Chamado.class);
    }

    @Override
    public Pagina<Chamado> listar(ConsultaChamados c) {
        Criteria criteria = criteria(c.filtros(), CampoData.ABERTURA);
        List<Criteria> extras = new ArrayList<>();
        if (c.busca() != null && !c.busca().isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(c.busca().trim()), Pattern.CASE_INSENSITIVE);
            extras.add(new Criteria().orOperator(
                    Criteria.where("titulo").regex(p),
                    Criteria.where("solicitante").regex(p),
                    Criteria.where("setor").regex(p)));
        }
        if (c.somenteAtrasados()) {
            List<Status> pendentes = List.of(Status.ABERTO, Status.EM_ATENDIMENTO, Status.AGUARDANDO);
            extras.add(Criteria.where("prazoSla").lt(c.agora()));
            extras.add(Criteria.where("status").in(nomes(pendentes)));
        }
        if (!extras.isEmpty()) {
            extras.add(0, criteria);
            criteria = new Criteria().andOperator(extras.toArray(new Criteria[0]));
        }
        Query query = new Query(criteria);
        long total = mongo.count(query, Chamado.class);
        String campo = ConsultaChamados.CAMPOS_ORDENACAO.contains(c.ordenarPor()) ? c.ordenarPor() : "dataAbertura";
        query.with(Sort.by(c.ascendente() ? Sort.Direction.ASC : Sort.Direction.DESC, campo))
                .skip((long) (c.pagina() - 1) * c.porPagina())
                .limit(c.porPagina());
        return new Pagina<>(mongo.find(query, Chamado.class), total, c.pagina(), c.porPagina());
    }

    @Override
    public Optional<Chamado> porId(String id) {
        return Optional.ofNullable(mongo.findById(id, Chamado.class));
    }

    @Override
    public Chamado salvar(Chamado chamado) {
        return mongo.save(chamado);
    }

    /** Sequência atômica na coleção "contadores" (findAndModify com $inc). */
    @Override
    public long proximoNumero() {
        Contador c = mongo.findAndModify(
                Query.query(Criteria.where("_id").is("chamados")),
                new Update().inc("valor", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Contador.class);
        return c.getValor();
    }

    @Override
    public long contar() {
        return mongo.count(new Query(), Chamado.class);
    }

    static Criteria criteria(Filtros f, CampoData campoData) {
        List<Criteria> partes = new ArrayList<>();
        if (f.dataInicio() != null || f.dataFim() != null) {
            Criteria data = Criteria.where(campoData.atributo());
            if (f.dataInicio() != null) data = data.gte(f.dataInicio().atStartOfDay());
            if (f.dataFim() != null) data = data.lte(LocalDateTime.of(f.dataFim(), LocalTime.MAX));
            partes.add(data);
        }
        if (!f.status().isEmpty()) partes.add(Criteria.where("status").in(nomes(f.status())));
        if (!f.prioridades().isEmpty()) partes.add(Criteria.where("prioridade").in(nomes(f.prioridades())));
        if (!f.categoriaIds().isEmpty()) partes.add(Criteria.where("categoriaId").in(f.categoriaIds()));
        if (!f.tecnicoIds().isEmpty()) partes.add(Criteria.where("tecnicoId").in(f.tecnicoIds()));
        return partes.isEmpty() ? new Criteria() : new Criteria().andOperator(partes.toArray(new Criteria[0]));
    }

    /** Enums são gravados pelo nome (ex.: "EM_ATENDIMENTO"). */
    private static List<String> nomes(java.util.Collection<? extends Enum<?>> valores) {
        return valores.stream().map(Enum::name).toList();
    }
}
