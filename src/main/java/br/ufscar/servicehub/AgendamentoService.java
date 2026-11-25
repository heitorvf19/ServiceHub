package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class AgendamentoService {
    private final Banco db;
    private final ServicoDAO servicos;
    private final Clock clock;

    AgendamentoService(Banco db, ServicoDAO servicos, Clock clock) {
        this.db = db;
        this.servicos = servicos;
        this.clock = clock;
    }

    @Transactional
    public long propor(long id, long usuario, Instant inicio, Instant fim) {
        var v = servicos.bloquear(id);
        v.exigirParticipante(usuario);
        v.exigirEstado("CONTRATADO", "AGENDADO");
        if (inicio == null
                || fim == null
                || !inicio.isAfter(clock.instant())
                || !fim.isAfter(inicio))
            throw Erro.campo("Escolha um início futuro e um término posterior ao início.");
        if (db.contar(
                        "SELECT COUNT(*) FROM agendamento WHERE servico_id=? AND estado='PENDENTE'",
                        id)
                > 0) throw Erro.conflito("Já existe uma proposta de horário aguardando resposta.");
        long horario =
                db.inserir(
                        "INSERT INTO agendamento(servico_id,autor_id,inicio,fim,estado)"
                            + " VALUES(?,?,?,?,'PENDENTE')",
                        id,
                        usuario,
                        inicio,
                        fim);
        servicos.registrar(id, usuario, "HORARIO_PROPOSTO", null, null, null, null);
        return horario;
    }

    @Transactional
    public Map<String, String> responder(long id, long horario, long usuario, Boolean aceitar) {
        if (aceitar == null) throw Erro.campo("Escolha aceitar ou recusar.");
        var v = servicos.bloquear(id);
        v.exigirParticipante(usuario);
        v.exigirEstado("CONTRATADO", "AGENDADO");
        var h = db.um("SELECT * FROM agendamento WHERE id=? AND servico_id=?", horario, id);
        if (Objects.equals(h.get("autorId"), usuario)) throw Erro.proibido();
        if (!"PENDENTE".equals(h.get("estado")))
            throw Erro.conflito("Essa proposta de horário já foi respondida.");
        if (!aceitar) {
            db.alterar("UPDATE agendamento SET estado='RECUSADO' WHERE id=?", horario);
            servicos.registrar(id, usuario, "HORARIO_RECUSADO", null, null, null, null);
            return Map.of("estado", "RECUSADO", "mensagem", "Horário recusado.");
        }
        // Serializa também confirmações de contratos diferentes do mesmo profissional.
        db.um("SELECT id FROM usuario WHERE id=? FOR UPDATE", v.profissional());
        var inicio = (Instant) h.get("inicio");
        boolean conflito =
                db.contar(
                                """
SELECT COUNT(*) FROM agendamento a JOIN contrato c ON c.servico_id=a.servico_id
JOIN solicitacao s ON s.id=c.servico_id
WHERE c.profissional_id=? AND c.servico_id<>? AND s.estado IN ('AGENDADO','EM_ANDAMENTO')
  AND a.estado='CONFIRMADO' AND a.inicio<? AND a.fim>?
""",
                                v.profissional(),
                                id,
                                h.get("fim"),
                                inicio)
                        > 0;
        if (conflito || !inicio.isAfter(clock.instant())) {
            String mensagem =
                    conflito
                            ? "O profissional tem outro serviço nesse intervalo. Proponha outro"
                                  + " horário."
                            : "Este horário já passou. Proponha outro horário.";
            db.alterar("UPDATE agendamento SET estado='INVALIDADO' WHERE id=?", horario);
            servicos.registrar(id, usuario, "HORARIO_INVALIDADO", null, null, mensagem, null);
            return Map.of("estado", "INVALIDADO", "mensagem", mensagem);
        }
        db.alterar(
                "UPDATE agendamento SET estado='SUBSTITUIDO' WHERE servico_id=? AND"
                    + " estado='CONFIRMADO'",
                id);
        db.alterar("UPDATE agendamento SET estado='CONFIRMADO' WHERE id=?", horario);
        servicos.transicao(v, usuario, "HORARIO_CONFIRMADO", "AGENDADO", null, null);
        return Map.of("estado", "CONFIRMADO", "mensagem", "Agendamento confirmado.");
    }
}
