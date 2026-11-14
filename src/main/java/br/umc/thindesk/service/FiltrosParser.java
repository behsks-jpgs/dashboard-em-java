package br.umc.thindesk.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.exception.DetalheErro;
import br.umc.thindesk.exception.ValidacaoException;

/**
 * Converte e valida os parâmetros de query string. Acumula TODOS os erros encontrados
 * e devolve em uma única resposta 400, para o cliente corrigir tudo de uma vez.
 *
 * Parâmetros: dataInicio, dataFim (AAAA-MM-DD), status, prioridade, categoriaId, tecnicoId
 * (os quatro últimos aceitam vários valores separados por vírgula).
 */
public final class FiltrosParser {

    private FiltrosParser() { }

    public static Filtros parse(Map<String, String> params) {
        List<DetalheErro> erros = new ArrayList<>();
        LocalDate inicio = data(params.get("dataInicio"), "dataInicio", erros);
        LocalDate fim = data(params.get("dataFim"), "dataFim", erros);
        Set<Status> status = enums(params.get("status"), "status", Status::deCodigo,
                Arrays.stream(Status.values()).map(Status::codigo).toList(), erros);
        Set<Prioridade> prioridades = enums(params.get("prioridade"), "prioridade", Prioridade::deCodigo,
                Arrays.stream(Prioridade.values()).map(Prioridade::codigo).toList(), erros);
        Set<String> categorias = new LinkedHashSet<>(lista(params.get("categoriaId")));
        Set<String> tecnicos = new LinkedHashSet<>(lista(params.get("tecnicoId")));

        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            erros.add(new DetalheErro("dataInicio", "dataInicio não pode ser posterior a dataFim."));
        }
        if (!erros.isEmpty()) throw new ValidacaoException(erros);
        return new Filtros(inicio, fim, Set.copyOf(status), Set.copyOf(prioridades),
                Set.copyOf(categorias), Set.copyOf(tecnicos));
    }

    public static int inteiro(Map<String, String> params, String nome, int padrao, int min, int max) {
        String valor = params.get(nome);
        if (valor == null || valor.isBlank()) return padrao;
        try {
            int n = Integer.parseInt(valor.trim());
            if (n < min || n > max) {
                throw new ValidacaoException(nome, "Deve estar entre " + min + " e " + max + ".");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ValidacaoException(nome, "Deve ser um número inteiro.");
        }
    }

    private static LocalDate data(String valor, String campo, List<DetalheErro> erros) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            erros.add(new DetalheErro(campo, "Data inválida. Use o formato AAAA-MM-DD."));
            return null;
        }
    }

    private static List<String> lista(String valor) {
        if (valor == null || valor.isBlank()) return List.of();
        return Arrays.stream(valor.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static <E> Set<E> enums(String valor, String campo, Function<String, Optional<E>> conversor,
                                    List<String> permitidos, List<DetalheErro> erros) {
        Set<E> resultado = new LinkedHashSet<>();
        List<String> invalidos = new ArrayList<>();
        for (String item : lista(valor)) {
            conversor.apply(item).ifPresentOrElse(resultado::add, () -> invalidos.add(item));
        }
        if (!invalidos.isEmpty()) {
            erros.add(new DetalheErro(campo, "Valor(es) inválido(s): " + String.join(", ", invalidos)
                    + ". Permitidos: " + permitidos.stream().collect(Collectors.joining(", ")) + "."));
        }
        return resultado;
    }
}
