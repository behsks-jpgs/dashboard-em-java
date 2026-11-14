package br.umc.thindesk.repository;

import java.util.List;
import java.util.Optional;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.service.Filtros;

/**
 * Porta de acesso aos chamados. A implementação de produção usa o MongoDB Atlas
 * ({@link MongoChamadoGateway}); os testes usam uma implementação em memória.
 */
public interface ChamadoGateway {

    /** Chamados que atendem aos filtros, aplicando o período sobre o campo de data informado. */
    List<Chamado> buscar(Filtros filtros, CampoData campoData);

    Pagina<Chamado> listar(ConsultaChamados consulta);

    Optional<Chamado> porId(String id);

    Chamado salvar(Chamado chamado);

    long proximoNumero();

    long contar();
}
