package br.umc.thindesk.repository;

import java.util.List;
import java.util.function.Function;

public record Pagina<T>(List<T> itens, long total, int pagina, int porPagina) {

    public int totalPaginas() {
        return (int) Math.ceil((double) total / porPagina);
    }

    public <R> Pagina<R> map(Function<T, R> f) {
        return new Pagina<>(itens.stream().map(f).toList(), total, pagina, porPagina);
    }
}
