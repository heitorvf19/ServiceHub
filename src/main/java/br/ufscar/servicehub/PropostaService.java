package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PropostaService {
    public record Oferta(BigDecimal valor, String escopo, BigDecimal duracaoHoras) {}

    private static final String PROJECAO =
            """
SELECT p.id,p.servico_id,p.profissional_id,p.valor,p.escopo,p.duracao_horas,p.estado,p.criada_em,
  u.nome AS profissional,s.titulo,s.estado AS estado_servico,k.nome AS categoria
FROM proposta p JOIN usuario u ON u.id=p.profissional_id JOIN solicitacao s ON s.id=p.servico_id
JOIN categoria k ON k.id=s.categoria_id
""";
    private final Banco db;
    private final ServicoDAO servicos;
    private final ContaService contas;

    PropostaService(Banco db, ServicoDAO servicos, ContaService contas) {
        this.db = db;
        this.servicos = servicos;
        this.contas = contas;
    }

    @Transactional
    public long enviar(long id, long usuario, Oferta p) {
        contas.exigirTipo(usuario, "PROFISSIONAL");
        var v = servicos.bloquear(id);
        v.exigirEstado("ABERTA");
        db.um("SELECT id FROM usuario WHERE id=? FOR UPDATE", usuario);
        if (!servicos.compativel(id, usuario)) throw Erro.proibido();
        if (db.contar(
                        "SELECT COUNT(*) FROM proposta WHERE servico_id=? AND profissional_id=? AND"
                            + " estado='PENDENTE'",
                        id,
                        usuario)
                > 0) throw Erro.conflito("Você já tem uma proposta pendente para este serviço.");
        var valor = Validar.decimal(p.valor, 10, true, "Valor");
        var duracao = Validar.decimal(p.duracaoHoras, 6, true, "Duração em horas");
        var escopo = Validar.texto(p.escopo, 20, 2000, "Escopo");
        long proposta =
                db.inserir(
                        "INSERT INTO"
                            + " proposta(servico_id,profissional_id,valor,escopo,duracao_horas,estado)"
                            + " VALUES(?,?,?,?,?,'PENDENTE')",
                        id,
                        usuario,
                        valor,
                        escopo,
                        duracao);
        db.alterar(
                "MERGE INTO conversa(servico_id,profissional_id) KEY(servico_id,profissional_id)"
                    + " VALUES(?,?)",
                id,
                usuario);
        db.alterar("UPDATE solicitacao SET atualizada_em=CURRENT_TIMESTAMP WHERE id=?", id);
        return proposta;
    }

    public List<Map<String, Object>> comparar(long id, long usuario) {
        servicos.vinculo(id).exigirCliente(usuario);
        var rows = db.listar(PROJECAO + " WHERE p.servico_id=? ORDER BY p.valor,p.id", id);
        for (var row : rows)
            row.put("reputacao", contas.reputacao((long) row.get("profissionalId")));
        return rows;
    }

    public List<Map<String, Object>> minhas(long usuario) {
        contas.exigirTipo(usuario, "PROFISSIONAL");
        return db.listar(
                PROJECAO + " WHERE p.profissional_id=? ORDER BY p.criada_em DESC,p.id DESC",
                usuario);
    }

    @Transactional
    public void retirar(long id, long proposta, long usuario, Boolean confirmar) {
        Validar.confirmar(confirmar);
        var v = servicos.bloquear(id);
        v.exigirEstado("ABERTA");
        var p =
                db.um(
                        "SELECT profissional_id,estado FROM proposta WHERE id=? AND servico_id=?",
                        proposta,
                        id);
        if (!Objects.equals(p.get("profissionalId"), usuario)) throw Erro.proibido();
        if (!"PENDENTE".equals(p.get("estado")))
            throw Erro.conflito("A proposta já foi encerrada.");
        db.alterar("UPDATE proposta SET estado='RETIRADA' WHERE id=?", proposta);
    }

    @Transactional
    public void contratar(long id, long proposta, long usuario, Boolean confirmar, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        Validar.confirmar(confirmar);
        if (servicos.repetida(op, id, usuario, "CONTRATACAO")) return;
        v.exigirEstado("ABERTA");
        var p = db.um("SELECT estado FROM proposta WHERE id=? AND servico_id=?", proposta, id);
        if (!"PENDENTE".equals(p.get("estado")))
            throw Erro.conflito("Esta proposta já não está pendente.");
        db.alterar(
                """
INSERT INTO contrato(servico_id,proposta_id,profissional_id,valor,escopo,duracao_horas)
  SELECT servico_id,id,profissional_id,valor,escopo,duracao_horas FROM proposta WHERE id=?
""",
                proposta);
        db.alterar(
                "UPDATE proposta SET estado=CASE WHEN id=? THEN 'ACEITA' ELSE 'NAO_SELECIONADA' END"
                    + " WHERE servico_id=? AND estado='PENDENTE'",
                proposta,
                id);
        servicos.transicao(v, usuario, "CONTRATACAO", "CONTRATADO", null, op);
    }
}
