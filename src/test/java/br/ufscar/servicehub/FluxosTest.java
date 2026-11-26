package br.ufscar.servicehub;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:fluxos;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "servicehub.demo=false"
        })
@AutoConfigureMockMvc
class FluxosTest {
    @Autowired Banco db;
    @Autowired ContaService contas;
    @Autowired SolicitacaoService pedidos;
    @Autowired PropostaService propostas;
    @Autowired AgendamentoService agenda;
    @Autowired ExecucaoService execucao;
    @Autowired InteracaoService interacao;
    @Autowired PagamentoService pagamentos;
    @Autowired ProvedorSimulado provedor;
    @Autowired MockMvc web;
    long cliente, profissional, outro;
    String prefixo;

    @BeforeEach
    void preparar() {
        prefixo = UUID.randomUUID().toString();
        cliente = conta("CLIENTE", "cliente");
        profissional = conta("PROFISSIONAL", "prof");
        outro = conta("PROFISSIONAL", "outro");
    }

    @Test
    void fotoInvalidaDesfazPublicacaoEPermiteReenvioComAMesmaChave() {
        var p = pedido();
        var incompleto =
                new SolicitacaoService.Pedido(
                        p.titulo(),
                        p.categoriaId(),
                        p.descricao(),
                        p.cidade(),
                        p.bairro(),
                        p.endereco(),
                        p.preferencias(),
                        p.frequencia(),
                        null,
                        null,
                        null,
                        List.of("foto inválida"));
        assertThatThrownBy(() -> pedidos.publicar(cliente, "mesma-chave", incompleto))
                .isInstanceOf(Erro.class);
        assertThat(db.contar("SELECT COUNT(*) FROM solicitacao WHERE cliente_id=?", cliente))
                .isZero();
        long id = pedidos.publicar(cliente, "mesma-chave", p);
        assertThat(db.contar("SELECT COUNT(*) FROM historico WHERE servico_id=?", id)).isOne();
    }

    long conta(String tipo, String nome) {
        return contas.cadastrar(
                new ContaService.Cadastro(
                        tipo,
                        "Pessoa " + nome,
                        nome + prefixo + "@exemplo.test",
                        "(16) 99999-0000",
                        "SenhaTeste123",
                        "SenhaTeste123",
                        "Executo serviços de manutenção residencial.",
                        "São Carlos",
                        "Centro",
                        List.of(1L)));
    }

    SolicitacaoService.Pedido pedido() {
        return new SolicitacaoService.Pedido(
                "Instalação de tomada",
                1L,
                "Preciso instalar uma tomada dupla na sala.",
                "São Carlos",
                "Centro",
                "Rua Privada, 12",
                null,
                "Uma vez",
                null,
                null,
                null,
                null);
    }

    long publicar() {
        return pedidos.publicar(cliente, UUID.randomUUID().toString(), pedido());
    }

    long ofertar(long id, long pro) {
        return propostas.enviar(
                id,
                pro,
                new PropostaService.Oferta(
                        new BigDecimal("125.50"),
                        "Instalação da tomada e verificação da rede.",
                        new BigDecimal("1.50")));
    }

    void contratar(long id) {
        propostas.contratar(id, ofertar(id, profissional), cliente, true, null);
    }

    void concluir(long id) {
        contratar(id);
        var futuro = Instant.now().plusSeconds(36000);
        long h = agenda.propor(id, cliente, futuro, futuro.plusSeconds(3600));
        agenda.responder(id, h, profissional, true);
        assertThatThrownBy(() -> execucao.iniciarExecucao(id, profissional, true, null))
                .isInstanceOf(Erro.class);
        // O relógio agendado já passou neste cenário, sem aguardar em tempo real.
        db.alterar(
                "UPDATE agendamento SET"
                    + " inicio=DATEADD('HOUR',-2,CURRENT_TIMESTAMP),fim=DATEADD('HOUR',-1,CURRENT_TIMESTAMP)"
                    + " WHERE id=?",
                h);
        execucao.iniciarExecucao(id, profissional, true, null);
        execucao.informarTermino(id, profissional, "Instalação finalizada e testada.", true, null);
        execucao.solicitarAjuste(id, cliente, "Rever o acabamento da parede.", null);
        assertThat(db.contar("SELECT COUNT(*) FROM pagamento WHERE servico_id=?", id)).isZero();
        execucao.informarTermino(
                id, profissional, "Acabamento corrigido e entrega revisada.", true, null);
        execucao.confirmarConclusao(id, cliente, true, null);
    }

    @Test
    void cicloCompletoComAjustePagamentoEAvaliacaoMutua() {
        long id = publicar();
        concluir(id);
        assertThat(pagamentos.consultar(id, cliente))
                .containsEntry("estado", "PENDENTE")
                .containsEntry("valor", new BigDecimal("125.50"));
        assertThatThrownBy(() -> pagamentos.iniciar(id, cliente)).isInstanceOf(Erro.class);
        contas.vincular(profissional, true);
        pagamentos.iniciar(id, cliente);
        UUID ref =
                (UUID)
                        db.um("SELECT referencia FROM tentativa WHERE servico_id=?", id)
                                .get("referencia");
        pagamentos.simular(id, cliente, ref, "RECUSADO");
        pagamentos.iniciar(id, cliente);
        UUID nova =
                (UUID)
                        db.um(
                                        "SELECT referencia FROM tentativa WHERE servico_id=? AND"
                                                + " estado='EM_PROCESSAMENTO'",
                                        id)
                                .get("referencia");
        var evento =
                new ProvedorSimulado.Evento(
                        UUID.randomUUID(), nova, id, new BigDecimal("125.50"), "PAGO");
        assertThatThrownBy(() -> pagamentos.confirmar(evento, "incorreta"))
                .isInstanceOf(Erro.class);
        var divergente =
                new ProvedorSimulado.Evento(UUID.randomUUID(), nova, id, BigDecimal.TEN, "PAGO");
        assertThatThrownBy(() -> pagamentos.confirmar(divergente, provedor.assinar(divergente)))
                .isInstanceOf(Erro.class);
        pagamentos.confirmar(evento, provedor.assinar(evento));
        pagamentos.confirmar(evento, provedor.assinar(evento));
        assertThat(pagamentos.iniciar(id, cliente).get("estado")).isEqualTo("PAGO");
        assertThat(db.contar("SELECT COUNT(*) FROM tentativa WHERE servico_id=?", id)).isEqualTo(2);
        assertThat(
                        db.contar(
                                "SELECT COUNT(*) FROM historico WHERE servico_id=? AND"
                                        + " evento='TERMINO'",
                                id))
                .isEqualTo(2);
        interacao.avaliar(id, cliente, 5, "Bom trabalho.");
        assertThat(contas.reputacao(profissional).get("totalAvaliacoes")).isEqualTo(0L);
        interacao.avaliar(id, profissional, 4, null);
        assertThat(contas.reputacao(profissional).get("totalAvaliacoes")).isEqualTo(1L);
        assertThatThrownBy(() -> interacao.avaliar(id, cliente, 1, null)).isInstanceOf(Erro.class);
    }

    @Test
    void publicacaoReenviadaPrivacidadeRetiradaEChat() {
        long id = pedidos.publicar(cliente, "reenvio", pedido());
        assertThat(pedidos.publicar(cliente, "reenvio", pedido())).isEqualTo(id);
        assertThat(pedidos.detalhes(id, profissional))
                .doesNotContainKeys("endereco", "telefoneParticipante", "historico");
        assertThatThrownBy(() -> pedidos.publicar(profissional, "fora", pedido()))
                .isInstanceOf(Erro.class);
        long retirada = ofertar(id, outro);
        propostas.retirar(id, retirada, outro, true);
        assertThatThrownBy(() -> pedidos.editar(id, cliente, pedido())).isInstanceOf(Erro.class);
        ofertar(id, outro);
        long escolhida = ofertar(id, profissional);
        long chat =
                (long)
                        db.um(
                                        "SELECT id FROM conversa WHERE servico_id=? AND"
                                                + " profissional_id=?",
                                        id,
                                        outro)
                                .get("id");
        interacao.mensagem(chat, outro, "Olá!");
        assertThatThrownBy(() -> interacao.conversa(chat, profissional)).isInstanceOf(Erro.class);
        propostas.contratar(id, escolhida, cliente, true, null);
        assertThatThrownBy(() -> interacao.mensagem(chat, outro, "Outra mensagem"))
                .isInstanceOf(Erro.class);
        assertThat(pedidos.detalhes(id, profissional)).containsKey("endereco");
        assertThat(pedidos.detalhes(id, outro)).doesNotContainKey("endereco");
        assertThatThrownBy(() -> pedidos.detalhes(id, conta("CLIENTE", "terceiro")))
                .isInstanceOf(Erro.class);
        assertThat(contas.perfil(profissional, false))
                .doesNotContainKeys("telefone", "email", "endereco");
        pedidos.cancelar(id, profissional, "Desistência antes do início.", true, null);
        assertThat(pedidos.detalhes(id, cliente)).containsEntry("estado", "CANCELADO");
    }

    @Test
    void colisaoDeAgendaPreservaHorarioAnteriorEAceitaIntervalosAdjacentes() {
        long um = publicar(), dois = publicar();
        contratar(um);
        contratar(dois);
        var t = Instant.now().plusSeconds(86400);
        long h1 = agenda.propor(um, cliente, t, t.plusSeconds(3600));
        assertThatThrownBy(() -> agenda.responder(um, h1, cliente, true)).isInstanceOf(Erro.class);
        agenda.responder(um, h1, profissional, true);
        long h2 = agenda.propor(dois, profissional, t.plusSeconds(3600), t.plusSeconds(7200));
        assertThat(agenda.responder(dois, h2, cliente, true).get("estado")).isEqualTo("CONFIRMADO");
        long conflito = agenda.propor(dois, cliente, t.plusSeconds(600), t.plusSeconds(4000));
        assertThat(agenda.responder(dois, conflito, profissional, true).get("estado"))
                .isEqualTo("INVALIDADO");
        assertThat(db.um("SELECT estado FROM agendamento WHERE id=?", h2))
                .containsEntry("estado", "CONFIRMADO");
        long novo = agenda.propor(dois, profissional, t.plusSeconds(7300), t.plusSeconds(8200));
        assertThat(agenda.responder(dois, novo, cliente, false).get("estado"))
                .isEqualTo("RECUSADO");
        assertThat(db.um("SELECT estado FROM agendamento WHERE id=?", h2))
                .containsEntry("estado", "CONFIRMADO");
    }

    @Test
    void avaliacoesIndividuaisPublicadasAposSeteDias() {
        long id = publicar();
        concluir(id);
        interacao.avaliar(id, cliente, 5, null);
        db.alterar(
                "UPDATE solicitacao SET concluida_em=DATEADD('DAY',-8,CURRENT_TIMESTAMP) WHERE"
                        + " id=?",
                id);
        assertThat(contas.reputacao(profissional).get("totalAvaliacoes")).isEqualTo(1L);
        assertThatThrownBy(() -> interacao.avaliar(id, profissional, 4, null))
                .isInstanceOf(Erro.class);
    }

    @Test
    void apiProtegeSessaoPerfilETitularidade() throws Exception {
        web.perform(get("/api/servicos")).andExpect(status().isUnauthorized());
        var login =
                web.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"prof"
                                                        + prefixo
                                                        + "@exemplo.test\",\"senha\":\"SenhaTeste123\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        var s = (MockHttpSession) login.getRequest().getSession(false);
        long id = publicar();
        web.perform(get("/api/servicos/" + id + "/propostas").session(s))
                .andExpect(status().isForbidden());
        web.perform(get("/api/servicos/" + id).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco").doesNotExist());
        web.perform(
                        post("/api/servicos/" + id + "/inicio")
                                .session(s)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmar\":true}"))
                .andExpect(status().isForbidden());
        web.perform(post("/api/auth/logout").session(s)).andExpect(status().isNoContent());
        web.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
    }

    @Test
    void limiteDeTentativasETrocaDeSenha() {
        var email = "cliente" + prefixo + "@exemplo.test";
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> contas.entrar(new ContaService.Credenciais(email, "invalida")))
                    .isInstanceOf(Erro.class);
        assertThatThrownBy(
                        () -> contas.entrar(new ContaService.Credenciais(email, "SenhaTeste123")))
                .hasMessageContaining("15 minutos");
        db.alterar(
                "UPDATE acesso SET bloqueio_ate=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE"
                        + " email=?",
                email);
        var sessao = contas.entrar(new ContaService.Credenciais(email, "SenhaTeste123"));
        String token = Senhas.segredo();
        db.alterar(
                "INSERT INTO recuperacao VALUES(?,?,?)",
                cliente,
                Senhas.digest(token),
                Instant.now().plusSeconds(180));
        contas.redefinir(token, "SenhaTrocada123", "SenhaTrocada123");
        assertThatThrownBy(() -> contas.redefinir(token, "MaisUmaSenha123", "MaisUmaSenha123"))
                .isInstanceOf(Erro.class);
        assertThat(contas.entrar(new ContaService.Credenciais(email, "SenhaTrocada123")).versao())
                .isGreaterThan(sessao.versao());
    }

    private List<Boolean> competir(Supplier<?> a, Supplier<?> b) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var largada = new CyclicBarrier(2);
        try {
            List<Future<Boolean>> respostas = new ArrayList<>();
            for (var operacao : List.of(a, b))
                respostas.add(
                        executor.submit(
                                () -> {
                                    largada.await(5, TimeUnit.SECONDS);
                                    try {
                                        operacao.get();
                                        return true;
                                    } catch (Erro conflito) {
                                        assertThat(conflito.status).isEqualTo(409);
                                        return false;
                                    }
                                }));
            return List.of(
                    respostas.get(0).get(15, TimeUnit.SECONDS),
                    respostas.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cliquesConcorrentesNaoDuplicamPropostasContratosOuCobrancas() throws Exception {
        long id = publicar();
        assertThat(competir(() -> ofertar(id, profissional), () -> ofertar(id, profissional)))
                .containsExactlyInAnyOrder(true, false);
        long a = (long) db.um("SELECT id FROM proposta WHERE servico_id=?", id).get("id"),
                b = ofertar(id, outro);
        assertThat(
                        competir(
                                () -> {
                                    propostas.contratar(id, a, cliente, true, null);
                                    return null;
                                },
                                () -> {
                                    propostas.contratar(id, b, cliente, true, null);
                                    return null;
                                }))
                .containsExactlyInAnyOrder(true, false);
        assertThat(db.contar("SELECT COUNT(*) FROM contrato WHERE servico_id=?", id)).isOne();
        long outroServico = publicar();
        concluir(outroServico);
        contas.vincular(profissional, true);
        assertThat(
                        competir(
                                () -> pagamentos.iniciar(outroServico, cliente),
                                () -> pagamentos.iniciar(outroServico, cliente)))
                .containsOnly(true);
        assertThat(db.contar("SELECT COUNT(*) FROM tentativa WHERE servico_id=?", outroServico))
                .isOne();
    }
}
