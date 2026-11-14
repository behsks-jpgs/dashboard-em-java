package br.umc.thindesk.repository;

/** Qual data o filtro de período deve considerar. */
public enum CampoData {
    ABERTURA("dataAbertura"),
    RESOLUCAO("dataResolucao");

    private final String atributo;

    CampoData(String atributo) { this.atributo = atributo; }

    public String atributo() { return atributo; }
}
