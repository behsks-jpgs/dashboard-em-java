package br.umc.thindesk.seed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.ConsideracaoTecnica;
import br.umc.thindesk.domain.HistoricoStatus;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.domain.TipoConsideracao;

/**
 * Gera uma massa de dados realista e REPRODUTÍVEL (semente fixa) para o dashboard:
 * ~12 meses de chamados em horário comercial, volume crescente, poucos nos fins de semana,
 * tempos de atendimento que variam por prioridade e categoria, e alguns estouros de SLA.
 */
public class GeradorDados {

    public record Dados(List<Categoria> categorias, List<Tecnico> tecnicos, List<Chamado> chamados) { }

    private record Cat(String id, String nome, String descricao, int peso, double horasMedias, String[] titulos) { }

    private static final List<Cat> CATEGORIAS = List.of(
            new Cat("cat-hardware", "Hardware", "Defeitos em computadores, monitores e periféricos", 14, 10,
                    new String[]{"Computador não liga", "Monitor piscando", "Teclado com teclas falhando",
                            "Mouse sem resposta", "Notebook superaquecendo", "Troca de HD por SSD"}),
            new Cat("cat-software", "Software", "Instalação, atualização e erros de programas", 18, 6,
                    new String[]{"Erro ao abrir planilha", "Instalar leitor de PDF", "Atualização do Windows travada",
                            "Antivírus expirado", "Programa fecha sozinho", "Licença do Office inválida"}),
            new Cat("cat-rede", "Rede e Internet", "Falta de conexão, lentidão, Wi-Fi e VPN", 16, 5,
                    new String[]{"Sem acesso à internet", "Wi-Fi caindo toda hora", "VPN não conecta",
                            "Rede muito lenta", "Cabo de rede danificado"}),
            new Cat("cat-impressoras", "Impressoras", "Impressão, digitalização e toner", 12, 4,
                    new String[]{"Impressora não imprime", "Papel atolado", "Trocar toner",
                            "Scanner não envia para e-mail", "Impressão saindo manchada"}),
            new Cat("cat-acesso", "Acesso e Senhas", "Criação de usuários, bloqueios e redefinição de senha", 20, 1.5,
                    new String[]{"Senha bloqueada", "Criar usuário para novo colaborador",
                            "Liberar acesso à pasta compartilhada", "Redefinir senha do sistema",
                            "Remover acesso de ex-colaborador"}),
            new Cat("cat-email", "E-mail", "Caixa de e-mail, Outlook e listas", 9, 3,
                    new String[]{"Caixa de e-mail cheia", "Não recebo e-mails externos",
                            "Configurar e-mail no celular", "Assinatura de e-mail"}),
            new Cat("cat-erp", "Sistema ERP", "Erros e dúvidas no sistema de gestão", 8, 12,
                    new String[]{"Erro ao emitir nota fiscal", "Relatório de vendas não gera",
                            "Estoque divergente no sistema", "Lentidão no ERP"}),
            new Cat("cat-telefonia", "Telefonia", "Ramais, headsets e linhas", 3, 8,
                    new String[]{"Ramal mudo", "Headset sem áudio", "Transferência de ramal"}));

    private static final List<Tecnico> TECNICOS = List.of(
            new Tecnico("tec-ana", "Ana Ribeiro", "ana.ribeiro@thindesk.local", "N1"),
            new Tecnico("tec-bruno", "Bruno Santos", "bruno.santos@thindesk.local", "N1"),
            new Tecnico("tec-carla", "Carla Mendes", "carla.mendes@thindesk.local", "N2"),
            new Tecnico("tec-diego", "Diego Alves", "diego.alves@thindesk.local", "N2"),
            new Tecnico("tec-eduarda", "Eduarda Lima", "eduarda.lima@thindesk.local", "N3"),
            new Tecnico("tec-fabio", "Fábio Costa", "fabio.costa@thindesk.local", "N1"));

    private static final String[] SETORES = {"Financeiro", "Comercial", "Estamparia", "Estoque", "RH",
                                             "Atendimento", "Diretoria"};
    private static final String[] SOLICITANTES = {"Mariana Souza", "João Pereira", "Patrícia Gomes",
            "Ricardo Nunes", "Luana Rocha", "Felipe Araújo", "Sandra Martins", "Thiago Barbosa", "Camila Freitas",
            "Gustavo Teixeira", "Renata Cardoso", "Paulo Henrique", "Vanessa Dias", "Lucas Moreira"};
    private static final String[] DIAGNOSTICOS = {"Verificado o equipamento no local; falha reproduzida.",
            "Logs analisados: erro recorrente após a última atualização.", "Acesso remoto realizado para análise.",
            "Problema causado por configuração incorreta.",
            "Testado em outra estação; falha isolada no equipamento do usuário."};
    private static final String[] SOLUCOES = {"Componente substituído e testado com o usuário.",
            "Configuração corrigida e serviço reiniciado.", "Reinstalação do programa resolveu o problema.",
            "Senha redefinida e acesso validado com o usuário.", "Driver atualizado; funcionamento normalizado."};
    private static final String[] OBSERVACOES = {"Aguardando retorno do usuário para validar.",
            "Peça solicitada ao fornecedor.", "Usuário em reunião; retorno combinado para mais tarde.",
            "Recomendado substituir o equipamento no próximo ciclo."};

    private final Random rnd;

    public GeradorDados(long semente) {
        this.rnd = new Random(semente);
    }

    public Dados gerar(LocalDateTime agora, int dias) {
        List<Categoria> categorias = CATEGORIAS.stream().map(c -> new Categoria(c.id(), c.nome(), c.descricao())).toList();
        List<Chamado> chamados = new ArrayList<>();
        LocalDate inicio = agora.toLocalDate().minusDays(dias);
        for (LocalDate dia = inicio; !dia.isAfter(agora.toLocalDate()); dia = dia.plusDays(1)) {
            boolean fimDeSemana = dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            double progresso = (double) (dia.toEpochDay() - inicio.toEpochDay()) / dias;
            double base = fimDeSemana ? 0.5 : 2.4 + 2.4 * progresso; // empresa crescendo ao longo do ano
            int qtd = (int) Math.max(0, base + rnd.nextGaussian() * 1.1);
            for (int i = 0; i < qtd; i++) {
                int hora = (int) Math.min(18, Math.max(8, 11.5 + rnd.nextGaussian() * 2.6));
                LocalDateTime abertura = dia.atTime(hora, rnd.nextInt(60), rnd.nextInt(60));
                if (abertura.isAfter(agora)) continue;
                chamados.add(chamado(abertura, agora));
            }
        }
        chamados.sort((a, b) -> a.getDataAbertura().compareTo(b.getDataAbertura()));
        for (int i = 0; i < chamados.size(); i++) chamados.get(i).setNumero(i + 1L);
        return new Dados(categorias, TECNICOS, chamados);
    }

    private Chamado chamado(LocalDateTime abertura, LocalDateTime agora) {
        Cat cat = sortearCategoria();
        Prioridade prioridade = sortearPrioridade();
        double idadeH = java.time.Duration.between(abertura, agora).toMinutes() / 60.0;
        double fator = switch (prioridade) {
            case CRITICA -> 0.3;
            case ALTA -> 0.6;
            case MEDIA -> 1.0;
            case BAIXA -> 1.6;
        };
        double respMin = Math.max(3, -Math.log(1 - rnd.nextDouble()) * 35 * fator);
        double resolH = Math.max(0.3, Math.exp(rnd.nextGaussian() * 0.7) * cat.horasMedias() * (0.6 + 0.5 * fator));

        Chamado c = new Chamado();
        String titulo = cat.titulos()[rnd.nextInt(cat.titulos().length)];
        c.setTitulo(titulo);
        c.setDescricao(titulo + ". Solicitação registrada pelo usuário via portal.");
        c.setSolicitante(SOLICITANTES[rnd.nextInt(SOLICITANTES.length)]);
        c.setSetor(SETORES[rnd.nextInt(SETORES.length)]);
        c.setCategoriaId(cat.id());
        c.setCategoriaNome(cat.nome());
        c.setPrioridade(prioridade);
        c.setDataAbertura(abertura);
        c.setPrazoSla(abertura.plusHours(prioridade.slaHoras()));
        c.setStatus(Status.ABERTO);
        c.getHistorico().add(new HistoricoStatus(null, Status.ABERTO, abertura, "Chamado aberto"));

        if (rnd.nextDouble() < 0.035 && idadeH > 2) {
            c.setStatus(Status.CANCELADO);
            c.getHistorico().add(new HistoricoStatus(Status.ABERTO, Status.CANCELADO, abertura.plusHours(1),
                    "Solicitação duplicada"));
            return c;
        }
        // ainda ninguém pegou (ou fila do dia ainda não triada)
        if (respMin / 60 >= idadeH || (idadeH < 30 && rnd.nextDouble() < 0.3)) return c;

        Tecnico tec = TECNICOS.get(rnd.nextInt(TECNICOS.size()));
        LocalDateTime primeiro = abertura.plusSeconds((long) (respMin * 60));
        c.setTecnicoId(tec.getId());
        c.setTecnicoNome(tec.getNome());
        c.setDataPrimeiroAtendimento(primeiro);
        c.setStatus(Status.EM_ATENDIMENTO);
        c.getHistorico().add(new HistoricoStatus(Status.ABERTO, Status.EM_ATENDIMENTO, primeiro, null));
        c.getConsideracoes().add(new ConsideracaoTecnica(TipoConsideracao.DIAGNOSTICO,
                DIAGNOSTICOS[rnd.nextInt(DIAGNOSTICOS.length)], tec.getId(), tec.getNome(),
                primeiro.plusMinutes(5 + rnd.nextInt(35))));

        // quanto mais antigo, maior a chance de já estar resolvido (sobra um pequeno backlog travado)
        double idadeDias = idadeH / 24;
        double chanceResolver = idadeDias < 3 ? 0.6 : idadeDias < 10 ? 0.88 : 0.988;
        if (resolH + respMin / 60 < idadeH && rnd.nextDouble() < chanceResolver) {
            LocalDateTime resolucao = primeiro.plusSeconds((long) (resolH * 3600));
            c.setStatus(Status.RESOLVIDO);
            c.setDataResolucao(resolucao);
            String solucao = SOLUCOES[rnd.nextInt(SOLUCOES.length)];
            c.getHistorico().add(new HistoricoStatus(Status.EM_ATENDIMENTO, Status.RESOLVIDO, resolucao, solucao));
            c.getConsideracoes().add(new ConsideracaoTecnica(TipoConsideracao.SOLUCAO, solucao,
                    tec.getId(), tec.getNome(), resolucao));
            if (rnd.nextDouble() < 0.6) {
                int r = rnd.nextInt(100);
                c.setAvaliacao(r < 45 ? 5 : r < 77 ? 4 : r < 90 ? 3 : r < 96 ? 2 : 1);
            }
        } else if (rnd.nextInt(3) == 0) {
            LocalDateTime quando = primeiro.plusMinutes(50);
            c.setStatus(Status.AGUARDANDO);
            c.getHistorico().add(new HistoricoStatus(Status.EM_ATENDIMENTO, Status.AGUARDANDO, quando, null));
            c.getConsideracoes().add(new ConsideracaoTecnica(TipoConsideracao.OBSERVACAO,
                    OBSERVACOES[rnd.nextInt(OBSERVACOES.length)], tec.getId(), tec.getNome(), quando));
        }
        return c;
    }

    private Cat sortearCategoria() {
        int total = CATEGORIAS.stream().mapToInt(Cat::peso).sum();
        int r = rnd.nextInt(total);
        for (Cat c : CATEGORIAS) {
            r -= c.peso();
            if (r < 0) return c;
        }
        return CATEGORIAS.get(0);
    }

    private Prioridade sortearPrioridade() {
        int r = rnd.nextInt(100);
        return r < 30 ? Prioridade.BAIXA : r < 72 ? Prioridade.MEDIA : r < 93 ? Prioridade.ALTA : Prioridade.CRITICA;
    }
}
