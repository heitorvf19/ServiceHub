package br.ufscar.servicehub;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
@ConditionalOnProperty(name = "servicehub.demo", havingValue = "true")
class DadosDemonstracao implements ApplicationRunner {
    private final Banco db;
    private final ContaService contas;
    private final SolicitacaoService solicitacoes;
    private final PropostaService propostas;
    private final ServicoDAO servicos;

    DadosDemonstracao(
            Banco db,
            ContaService contas,
            SolicitacaoService solicitacoes,
            PropostaService propostas,
            ServicoDAO servicos) {
        this.db = db;
        this.contas = contas;
        this.solicitacoes = solicitacoes;
        this.propostas = propostas;
        this.servicos = servicos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (db.contar("SELECT COUNT(*) FROM usuario") > 0) return;
        long maria =
                contas.cadastrar(
                        new ContaService.Cadastro(
                                "CLIENTE",
                                "Maria Santos",
                                "cliente@servicehub.test",
                                "(16) 99900-0101",
                                "ServiceHub123",
                                "ServiceHub123",
                                null,
                                null,
                                null,
                                null));
        long carlos =
                profissional("Carlos Pereira", "profissional@servicehub.test", List.of(1L, 3L, 5L));
        long ana = profissional("Ana Costa", "ana@servicehub.test", List.of(1L, 2L, 4L));
        contas.vincular(carlos, true);
        contas.vincular(ana, true);
        try (var in = new ClassPathResource("static/assets/eletrica.jpeg").getInputStream()) {
            String imagem =
                    "data:image/jpeg;base64,"
                            + Base64.getEncoder().encodeToString(in.readAllBytes());
            long id =
                    pedido(
                            maria,
                            1,
                            "Instalação de chuveiro elétrico",
                            "Preciso instalar um chuveiro no banheiro. Já comprei o aparelho e os"
                                + " materiais.",
                            List.of(imagem));
            oferta(
                    id,
                    carlos,
                    "175.00",
                    "Instalação do chuveiro e teste de funcionamento com os materiais fornecidos.");
            oferta(
                    id,
                    ana,
                    "195.00",
                    "Verificação da fiação e instalação do chuveiro, incluindo o teste final.");
        }
        pedido(
                maria,
                2,
                "Limpeza completa do apartamento",
                "Faxina em três quartos, sala, cozinha e janelas do apartamento.",
                List.of());
        long agendado =
                pedido(
                        maria,
                        3,
                        "Conserto de torneira",
                        "A torneira da cozinha está vazando. Preciso trocar o reparo e verificar a"
                            + " vedação.",
                        List.of());
        long prop =
                oferta(
                        agendado,
                        carlos,
                        "120.00",
                        "Troca do reparo da torneira e teste de vedação na cozinha.");
        propostas.contratar(agendado, prop, maria, true, null);
        var agora = Instant.now();
        db.inserir(
                "INSERT INTO agendamento(servico_id,autor_id,inicio,fim,estado)"
                    + " VALUES(?,?,?,?,'CONFIRMADO')",
                agendado,
                maria,
                agora.minusSeconds(1800),
                agora.plusSeconds(1800));
        servicos.transicao(
                servicos.vinculo(agendado), carlos, "HORARIO_CONFIRMADO", "AGENDADO", null, null);
        db.alterar(
                "UPDATE solicitacao SET"
                    + " criada_em=DATEADD('DAY',-1,criada_em),atualizada_em=DATEADD('HOUR',-1,atualizada_em)"
                    + " WHERE id=?",
                agendado);
        db.alterar(
                "UPDATE historico SET data_hora=DATEADD('DAY',-1,data_hora) WHERE servico_id=?",
                agendado);
        db.alterar(
                "UPDATE proposta SET criada_em=DATEADD('DAY',-1,criada_em) WHERE servico_id=?",
                agendado);
        db.alterar(
                "UPDATE contrato SET contratado_em=DATEADD('DAY',-1,contratado_em) WHERE"
                    + " servico_id=?",
                agendado);
    }

    private long profissional(String nome, String email, List<Long> categorias) {
        return contas.cadastrar(
                new ContaService.Cadastro(
                        "PROFISSIONAL",
                        nome,
                        email,
                        "(16) 99900-0202",
                        "ServiceHub123",
                        "ServiceHub123",
                        "Atendo serviços residenciais em São Carlos, com orçamento e horário"
                            + " combinados.",
                        "São Carlos",
                        "Centro, Vila Prado",
                        categorias));
    }

    private long pedido(
            long cliente, long categoria, String titulo, String descricao, List<String> fotos) {
        return solicitacoes.publicar(
                cliente,
                UUID.randomUUID().toString(),
                new SolicitacaoService.Pedido(
                        titulo,
                        categoria,
                        descricao,
                        "São Carlos",
                        "Centro",
                        "Rua das Flores, 123",
                        null,
                        "Uma vez",
                        null,
                        null,
                        null,
                        fotos));
    }

    private long oferta(long id, long usuario, String valor, String escopo) {
        return propostas.enviar(
                id,
                usuario,
                new PropostaService.Oferta(new BigDecimal(valor), escopo, new BigDecimal("2")));
    }
}
