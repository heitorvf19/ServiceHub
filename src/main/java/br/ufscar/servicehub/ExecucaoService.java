package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class ExecucaoService {
    private final Banco db;
    private final Clock clock;
    private final ServicoDAO servicos;

    ExecucaoService(Banco db, ServicoDAO servicos, Clock clock) {
        this.db = db;
        this.servicos = servicos;
        this.clock = clock;
    }

    @Transactional
    public void iniciarExecucao(long id, long usuario, Boolean confirmar, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirProfissional(usuario);
        Validar.confirmar(confirmar);
        if (servicos.repetida(op, id, usuario, "INICIO")) return;
        v.exigirEstado("AGENDADO");
        var h =
                db.um(
                        "SELECT inicio FROM agendamento WHERE servico_id=? AND estado='CONFIRMADO'",
                        id);
        var agora = clock.instant();
        if (((Instant) h.get("inicio")).isAfter(agora))
            throw Erro.conflito("Aguarde o início do horário confirmado.");
        db.alterar(
                "UPDATE agendamento SET estado='INVALIDADO' WHERE servico_id=? AND"
                    + " estado='PENDENTE'",
                id);
        db.alterar("UPDATE solicitacao SET inicio_real=? WHERE id=?", agora, id);
        servicos.transicao(v, usuario, "INICIO", "EM_ANDAMENTO", null, op);
    }

    @Transactional
    public void checklist(long id, long usuario, int item, Boolean marcado) {
        var v = servicos.bloquear(id);
        v.exigirProfissional(usuario);
        v.exigirEstado("EM_ANDAMENTO");
        if (item < 1 || item > 5 || marcado == null)
            throw Erro.campo("Item de checklist inválido.");
        if (marcado)
            db.alterar(
                    "MERGE INTO item_execucao(servico_id,item) KEY(servico_id,item) VALUES(?,?)",
                    id,
                    item);
        else db.alterar("DELETE FROM item_execucao WHERE servico_id=? AND item=?", id, item);
    }

    @Transactional
    public void informarTermino(long id, long usuario, String relato, Boolean confirmar, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirProfissional(usuario);
        Validar.confirmar(confirmar);
        if (servicos.repetida(op, id, usuario, "TERMINO")) return;
        v.exigirEstado("EM_ANDAMENTO");
        servicos.transicao(
                v,
                usuario,
                "TERMINO",
                "AGUARDANDO_CONFIRMACAO",
                Validar.texto(relato, 10, 1000, "Relato"),
                op);
    }

    @Transactional
    public void solicitarAjuste(long id, long usuario, String descricao, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        if (servicos.repetida(op, id, usuario, "AJUSTE")) return;
        v.exigirEstado("AGUARDANDO_CONFIRMACAO");
        servicos.transicao(
                v,
                usuario,
                "AJUSTE",
                "EM_ANDAMENTO",
                Validar.texto(descricao, 10, 1000, "Descrição do ajuste"),
                op);
    }

    @Transactional
    public void confirmarConclusao(long id, long usuario, Boolean confirmar, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        Validar.confirmar(confirmar);
        if (servicos.repetida(op, id, usuario, "CONCLUSAO") || v.estado().equals("CONCLUIDO"))
            return;
        v.exigirEstado("AGUARDANDO_CONFIRMACAO");
        db.alterar("UPDATE solicitacao SET concluida_em=? WHERE id=?", clock.instant(), id);
        db.alterar(
                "INSERT INTO pagamento(servico_id,valor,estado) SELECT servico_id,valor,'PENDENTE'"
                    + " FROM contrato WHERE servico_id=?",
                id);
        servicos.transicao(v, usuario, "CONCLUSAO", "CONCLUIDO", null, op);
    }
}
