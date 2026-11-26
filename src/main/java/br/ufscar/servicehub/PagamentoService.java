package br.ufscar.servicehub;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

@Service
public class PagamentoService {
    private static final Logger LOG = LoggerFactory.getLogger(PagamentoService.class);
    private final Banco db;
    private final ServicoDAO servicos;
    private final ProvedorSimulado provedor;
    private final Clock clock;

    PagamentoService(Banco db, ServicoDAO servicos, ProvedorSimulado provedor, Clock clock) {
        this.db = db;
        this.servicos = servicos;
        this.provedor = provedor;
        this.clock = clock;
    }

    public Map<String, Object> consultar(long id, long usuario) {
        servicos.vinculo(id).exigirParticipante(usuario);
        return registro(id);
    }

    private Map<String, Object> registro(long id) {
        var p =
                db.um(
                        "SELECT servico_id,valor,estado,pago_em FROM pagamento WHERE servico_id=?",
                        id);
        p.put(
                "tentativas",
                db.listar(
                        "SELECT referencia,estado,valor,criada_em FROM tentativa WHERE servico_id=?"
                            + " ORDER BY criada_em DESC",
                        id));
        return p;
    }

    @Transactional
    public Map<String, Object> iniciar(long id, long usuario) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        v.exigirEstado("CONCLUIDO");
        var p = registro(id);
        if (Set.of("PAGO", "EM_PROCESSAMENTO").contains(p.get("estado"))) return p;
        if (db.contar(
                        "SELECT COUNT(*) FROM usuario WHERE id=? AND recebedor IS NOT NULL",
                        v.profissional())
                == 0)
            throw Erro.conflito("O profissional precisa vincular a conta recebedora no perfil.");
        db.alterar(
                "INSERT INTO tentativa(referencia,servico_id,valor,estado)"
                    + " VALUES(?,?,?,'EM_PROCESSAMENTO')",
                UUID.randomUUID(),
                id,
                p.get("valor"));
        db.alterar("UPDATE pagamento SET estado='EM_PROCESSAMENTO' WHERE servico_id=?", id);
        servicos.registrar(id, usuario, "COBRANCA_INICIADA", null, null, null, null);
        return registro(id);
    }

    @Transactional
    public Map<String, Object> simular(long id, long usuario, UUID referencia, String resultado) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        v.exigirEstado("CONCLUIDO");
        var t =
                db.um(
                        "SELECT valor,estado FROM tentativa WHERE referencia=? AND servico_id=?",
                        referencia,
                        id);
        if (!"EM_PROCESSAMENTO".equals(t.get("estado"))) {
            if (Objects.equals(resultado, t.get("estado"))) return registro(id);
            throw Erro.conflito("Esta tentativa já foi encerrada.");
        }
        var e =
                new ProvedorSimulado.Evento(
                        UUID.randomUUID(), referencia, id, (BigDecimal) t.get("valor"), resultado);
        return confirmar(e, provedor.assinar(e));
    }

    @Transactional
    public Map<String, Object> confirmar(ProvedorSimulado.Evento e, String assinatura) {
        provedor.verificar(e, assinatura);
        var t = db.um("SELECT servico_id FROM tentativa WHERE referencia=?", e.referencia());
        long id = (long) t.get("servicoId");
        servicos.bloquear(id);
        var tentativa =
                db.um("SELECT valor,estado FROM tentativa WHERE referencia=?", e.referencia());
        if (e.servicoId() != id || e.valor().compareTo((BigDecimal) tentativa.get("valor")) != 0)
            throw divergencia(e);
        var anterior =
                db.opcional(
                        "SELECT referencia,resultado FROM evento_provedor WHERE evento_id=?",
                        e.eventoId());
        if (anterior != null) {
            if (!e.referencia().equals(anterior.get("referencia"))
                    || !e.resultado().equals(anterior.get("resultado"))) throw divergencia(e);
            return registro(id);
        }
        if (!"EM_PROCESSAMENTO".equals(tentativa.get("estado"))) {
            if (e.resultado().equals(tentativa.get("estado"))) return registro(id);
            throw divergencia(e);
        }
        db.alterar(
                "INSERT INTO evento_provedor(evento_id,referencia,servico_id,valor,resultado)"
                    + " VALUES(?,?,?,?,?)",
                e.eventoId(),
                e.referencia(),
                id,
                e.valor(),
                e.resultado());
        db.alterar(
                "UPDATE tentativa SET estado=? WHERE referencia=?", e.resultado(), e.referencia());
        db.alterar(
                "UPDATE pagamento SET estado=?,pago_em=? WHERE servico_id=?",
                e.resultado(),
                e.resultado().equals("PAGO") ? clock.instant() : null,
                id);
        servicos.registrar(
                id,
                null,
                "PAGAMENTO_" + e.resultado(),
                null,
                null,
                e.referencia().toString(),
                null);
        return registro(id);
    }

    private Erro divergencia(ProvedorSimulado.Evento e) {
        LOG.warn(
                "Conciliação de pagamento necessária. Evento {} tentativa {}",
                e.eventoId(),
                e.referencia());
        return Erro.conflito("Evento divergente. Encaminhado para conciliação.");
    }
}
