package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class SolicitacaoService {
    public record Pedido(
            String titulo,
            Long categoriaId,
            String descricao,
            String cidade,
            String bairro,
            String endereco,
            String preferencias,
            String frequencia,
            Instant dataPreferida,
            BigDecimal orcamentoMin,
            BigDecimal orcamentoMax,
            List<String> fotos) {}

    private static final String CAMPOS =
            """
SELECT s.id,s.titulo,s.descricao,s.cidade,s.bairro,s.estado,s.categoria_id,k.nome AS categoria,
  s.cliente_id,u.nome AS cliente,s.preferencias,s.frequencia,s.data_preferida,s.orcamento_min,s.orcamento_max,
  s.criada_em,s.atualizada_em,s.inicio_real,s.concluida_em
FROM solicitacao s JOIN categoria k ON k.id=s.categoria_id JOIN usuario u ON u.id=s.cliente_id
""";
    private final Banco db;
    private final ContaService contas;
    private final ServicoDAO servicos;
    private final FotosService fotos;

    SolicitacaoService(Banco db, ContaService contas, ServicoDAO servicos, FotosService fotos) {
        this.db = db;
        this.contas = contas;
        this.servicos = servicos;
        this.fotos = fotos;
    }

    private Pedido validar(Pedido d) {
        contas.categoriaAtiva(d.categoriaId);
        var min =
                d.orcamentoMin == null
                        ? null
                        : Validar.decimal(d.orcamentoMin, 10, false, "Orçamento mínimo");
        var max =
                d.orcamentoMax == null
                        ? null
                        : Validar.decimal(d.orcamentoMax, 10, false, "Orçamento máximo");
        if (min != null && max != null && min.compareTo(max) > 0)
            throw Erro.campo("O orçamento máximo deve cobrir o mínimo.");
        if (d.frequencia != null
                && !List.of("Uma vez", "Semanal", "Quinzenal", "Mensal").contains(d.frequencia))
            throw Erro.campo("Escolha uma frequência válida.");
        return new Pedido(
                Validar.texto(d.titulo, 5, 100, "Título"),
                d.categoriaId,
                Validar.texto(d.descricao, 20, 2000, "Descrição"),
                Validar.texto(d.cidade, 1, 100, "Cidade"),
                Validar.texto(d.bairro, 1, 100, "Bairro"),
                Validar.texto(d.endereco, 1, 300, "Endereço"),
                Validar.opcional(d.preferencias, 1000, "Preferências"),
                d.frequencia,
                d.dataPreferida,
                min,
                max,
                d.fotos);
    }

    @Transactional
    public long publicar(long usuario, String chave, Pedido dados) {
        contas.exigirTipo(usuario, "CLIENTE");
        chave = Validar.texto(chave, 1, 80, "Idempotency-Key");
        db.um("SELECT id FROM usuario WHERE id=? FOR UPDATE", usuario);
        var anterior =
                db.opcional(
                        "SELECT id FROM solicitacao WHERE cliente_id=? AND chave=?",
                        usuario,
                        chave);
        if (anterior != null) return (long) anterior.get("id");
        var d = validar(dados);
        long id =
                db.inserir(
                        """
INSERT INTO solicitacao(cliente_id,chave,estado,titulo,categoria_id,descricao,cidade,bairro,endereco,
  preferencias,frequencia,data_preferida,orcamento_min,orcamento_max) VALUES(?,?,'ABERTA',?,?,?,?,?,?,?,?,?,?,?)
""",
                        usuario,
                        chave,
                        d.titulo,
                        d.categoriaId,
                        d.descricao,
                        d.cidade,
                        d.bairro,
                        d.endereco,
                        d.preferencias,
                        d.frequencia,
                        d.dataPreferida,
                        d.orcamentoMin,
                        d.orcamentoMax);
        fotos.adicionar(id, "PEDIDO", d.fotos);
        servicos.registrar(id, usuario, "PUBLICACAO", null, "ABERTA", null, null);
        return id;
    }

    @Transactional
    public void editar(long id, long usuario, Pedido dados) {
        var v = servicos.bloquear(id);
        v.exigirCliente(usuario);
        v.exigirEstado("ABERTA");
        if (db.contar("SELECT COUNT(*) FROM proposta WHERE servico_id=?", id) > 0)
            throw Erro.conflito(
                    "Esta solicitação já recebeu proposta. Para alterar os termos, cancele e"
                        + " publique outra.");
        var d = validar(dados);
        db.alterar(
                """
UPDATE solicitacao SET titulo=?,categoria_id=?,descricao=?,cidade=?,bairro=?,endereco=?,preferencias=?,
  frequencia=?,data_preferida=?,orcamento_min=?,orcamento_max=? WHERE id=?
""",
                d.titulo,
                d.categoriaId,
                d.descricao,
                d.cidade,
                d.bairro,
                d.endereco,
                d.preferencias,
                d.frequencia,
                d.dataPreferida,
                d.orcamentoMin,
                d.orcamentoMax,
                id);
        if (d.fotos != null) {
            db.alterar("DELETE FROM foto WHERE servico_id=? AND etapa='PEDIDO'", id);
            fotos.adicionar(id, "PEDIDO", d.fotos);
        }
        servicos.registrar(id, usuario, "EDICAO", null, null, null, null);
    }

    public List<Map<String, Object>> buscar(
            long usuario, Long categoria, String bairro, String texto) {
        contas.exigirTipo(usuario, "PROFISSIONAL");
        var sql =
                new StringBuilder(CAMPOS)
                        .append(
                                """
 JOIN atuacao a ON a.categoria_id=s.categoria_id JOIN usuario p ON p.id=a.profissional_id
 WHERE p.id=? AND s.estado='ABERTA' AND LOWER(p.cidade)=LOWER(s.cidade)
""");
        var args = new ArrayList<Object>();
        args.add(usuario);
        if (categoria != null) {
            sql.append(" AND s.categoria_id=?");
            args.add(categoria);
        }
        if (bairro != null && !bairro.isBlank()) {
            sql.append(" AND LOCATE(LOWER(?),LOWER(s.bairro))>0");
            args.add(bairro.strip());
        }
        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOCATE(LOWER(?),LOWER(s.titulo || ' ' || s.descricao))>0");
            args.add(texto.strip());
        }
        var lista = db.listar(sql + " ORDER BY s.criada_em DESC,s.id DESC", args.toArray());
        for (var row : lista)
            row.put("reputacaoCliente", contas.reputacao((long) row.get("clienteId")));
        return lista;
    }

    public List<Map<String, Object>> meus(
            long usuario, String estado, LocalDate de, LocalDate ate) {
        var sql =
                new StringBuilder(CAMPOS)
                        .append(
                                " LEFT JOIN contrato c ON c.servico_id=s.id WHERE (s.cliente_id=?"
                                    + " OR c.profissional_id=?)");
        var args = new ArrayList<Object>();
        args.add(usuario);
        args.add(usuario);
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND s.estado=?");
            args.add(estado);
        }
        if (de != null && ate != null && de.isAfter(ate))
            throw Erro.campo("Confira o período informado.");
        var fuso = ZoneId.of("America/Sao_Paulo");
        if (de != null) {
            sql.append(" AND s.atualizada_em>=?");
            args.add(de.atStartOfDay(fuso).toInstant());
        }
        if (ate != null) {
            sql.append(" AND s.atualizada_em<?");
            args.add(ate.plusDays(1).atStartOfDay(fuso).toInstant());
        }
        var linhas = db.listar(sql + " ORDER BY s.atualizada_em DESC,s.id DESC", args.toArray());
        for (var linha : linhas) {
            Object id = linha.get("id");
            linha.put(
                    "propostasPendentes",
                    db.contar(
                            "SELECT COUNT(*) FROM proposta WHERE servico_id=? AND"
                                + " estado='PENDENTE'",
                            id));
            linha.put("contrato", contrato(id));
            linha.put(
                    "pagamento",
                    db.opcional(
                            "SELECT valor,estado,pago_em FROM pagamento WHERE servico_id=?", id));
        }
        return linhas;
    }

    private Map<String, Object> contrato(Object id) {
        return db.opcional(
                """
SELECT c.proposta_id,c.profissional_id,u.nome AS profissional,c.valor,c.escopo,c.duracao_horas,c.contratado_em
FROM contrato c JOIN usuario u ON u.id=c.profissional_id WHERE c.servico_id=?
""",
                id);
    }

    public Map<String, Object> detalhes(long id, long usuario) {
        var v = servicos.vinculo(id);
        boolean participante = v.titular(usuario) || v.contratado(usuario);
        boolean proposta =
                db.contar(
                                "SELECT COUNT(*) FROM proposta WHERE servico_id=? AND"
                                    + " profissional_id=?",
                                id,
                                usuario)
                        > 0;
        if (!participante
                && !proposta
                && !(v.estado().equals("ABERTA") && servicos.compativel(id, usuario)))
            throw Erro.ausente();
        var d = db.um(CAMPOS + " WHERE s.id=?", id);
        d.put("reputacaoCliente", contas.reputacao(v.cliente()));
        d.put(
                "fotos",
                db.listar(
                        "SELECT id,etapa FROM foto WHERE servico_id=? AND (etapa='PEDIDO' OR ?)"
                            + " ORDER BY id",
                        id,
                        participante));
        if (participante) {
            d.put(
                    "endereco",
                    db.um("SELECT endereco FROM solicitacao WHERE id=?", id).get("endereco"));
            d.put("contrato", contrato(id));
            d.put(
                    "historico",
                    db.listar(
                            """
SELECT h.id,h.evento,h.origem,h.destino,h.detalhe,h.operacao,h.data_hora,u.nome AS autor
FROM historico h LEFT JOIN usuario u ON u.id=h.autor_id WHERE h.servico_id=? ORDER BY h.id
""",
                            id));
            d.put(
                    "horarios",
                    db.listar(
                            "SELECT id,autor_id,inicio,fim,estado FROM agendamento WHERE"
                                + " servico_id=? ORDER BY id DESC",
                            id));
            d.put(
                    "checklist",
                    db.listar(
                            "SELECT item FROM item_execucao WHERE servico_id=? ORDER BY item", id));
            d.put(
                    "pagamento",
                    db.opcional(
                            "SELECT valor,estado,pago_em FROM pagamento WHERE servico_id=?", id));
            d.put(
                    "minhaAvaliacao",
                    db.opcional(
                            "SELECT nota,comentario FROM avaliacao WHERE servico_id=? AND"
                                + " autor_id=?",
                            id,
                            usuario));
            d.put(
                    "avaliacoesPublicadas",
                    db.listar(
                            "SELECT autor_id,nota,comentario FROM avaliacao_publicada WHERE"
                                + " servico_id=? ORDER BY id",
                            id));
            if (v.profissional() != null)
                d.put(
                        "telefoneParticipante",
                        db.um(
                                        "SELECT telefone FROM usuario WHERE id=?",
                                        v.titular(usuario) ? v.profissional() : v.cliente())
                                .get("telefone"));
        }
        return d;
    }

    @Transactional
    public void cancelar(long id, long usuario, String motivo, Boolean confirmar, UUID op) {
        var v = servicos.bloquear(id);
        v.exigirParticipante(usuario);
        Validar.confirmar(confirmar);
        if (servicos.repetida(op, id, usuario, "CANCELAMENTO")) return;
        v.exigirEstado("ABERTA", "CONTRATADO", "AGENDADO");
        motivo = Validar.texto(motivo, 10, 500, "Motivo");
        db.alterar(
                "UPDATE proposta SET estado='ENCERRADA' WHERE servico_id=? AND estado='PENDENTE'",
                id);
        db.alterar(
                "UPDATE agendamento SET estado='CANCELADO' WHERE servico_id=? AND estado IN"
                    + " ('CONFIRMADO','PENDENTE')",
                id);
        servicos.transicao(v, usuario, "CANCELAMENTO", "CANCELADO", motivo, op);
    }
}
