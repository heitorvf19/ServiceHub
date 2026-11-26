package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
class InteracaoService {
    private final Banco db;
    private final ServicoDAO servicos;
    private final Clock clock;

    InteracaoService(Banco db, ServicoDAO servicos, Clock clock) {
        this.db = db;
        this.servicos = servicos;
        this.clock = clock;
    }

    private static final String CONVERSAS =
            """
SELECT c.id,c.servico_id,c.profissional_id,s.cliente_id,s.titulo,u.nome AS profissional,v.nome AS cliente,
  (s.estado='ABERTA' OR (s.estado NOT IN ('CONCLUIDO','CANCELADO') AND k.profissional_id=c.profissional_id)) AS permite_enviar
FROM conversa c JOIN solicitacao s ON s.id=c.servico_id JOIN usuario u ON u.id=c.profissional_id
JOIN usuario v ON v.id=s.cliente_id LEFT JOIN contrato k ON k.servico_id=s.id
""";

    List<Map<String, Object>> conversas(long usuario) {
        return db.listar(
                CONVERSAS + " WHERE c.profissional_id=? OR s.cliente_id=? ORDER BY c.id DESC",
                usuario,
                usuario);
    }

    Map<String, Object> conversa(long id, long usuario) {
        var c =
                db.um(
                        CONVERSAS + " WHERE c.id=? AND (c.profissional_id=? OR s.cliente_id=?)",
                        id,
                        usuario,
                        usuario);
        c.put(
                "mensagens",
                db.listar(
                        "SELECT m.id,m.autor_id,u.nome AS autor,m.texto,m.enviada_em FROM mensagem"
                            + " m JOIN usuario u ON u.id=m.autor_id WHERE conversa_id=? ORDER BY"
                            + " m.enviada_em,m.id",
                        id));
        return c;
    }

    @Transactional
    public long mensagem(long id, long usuario, String texto) {
        var ref = db.um("SELECT servico_id FROM conversa WHERE id=?", id);
        servicos.bloquear((long) ref.get("servicoId"));
        var c =
                db.um(
                        CONVERSAS + " WHERE c.id=? AND (c.profissional_id=? OR s.cliente_id=?)",
                        id,
                        usuario,
                        usuario);
        if (!Boolean.TRUE.equals(c.get("permiteEnviar")))
            throw Erro.conflito("Esta conversa está disponível apenas para leitura.");
        return db.inserir(
                "INSERT INTO mensagem(conversa_id,autor_id,texto) VALUES(?,?,?)",
                id,
                usuario,
                Validar.texto(texto, 1, 2000, "Mensagem"));
    }

    @Transactional
    public void avaliar(long id, long usuario, Integer nota, String comentario) {
        var v = servicos.bloquear(id);
        v.exigirParticipante(usuario);
        v.exigirEstado("CONCLUIDO");
        var fim =
                (Instant)
                        db.um("SELECT concluida_em FROM solicitacao WHERE id=?", id)
                                .get("concluidaEm");
        if (!clock.instant().isBefore(fim.plus(Duration.ofDays(7))))
            throw Erro.conflito("O prazo de avaliação terminou.");
        if (nota == null || nota < 1 || nota > 5)
            throw Erro.campo("A nota deve ser um inteiro de 1 a 5.");
        if (db.contar(
                        "SELECT COUNT(*) FROM avaliacao WHERE servico_id=? AND autor_id=?",
                        id,
                        usuario)
                > 0) throw Erro.conflito("Você já avaliou este serviço.");
        long outro = v.titular(usuario) ? v.profissional() : v.cliente();
        db.inserir(
                "INSERT INTO avaliacao(servico_id,autor_id,avaliado_id,nota,comentario)"
                    + " VALUES(?,?,?,?,?)",
                id,
                usuario,
                outro,
                nota,
                Validar.opcional(comentario, 500, "Comentário"));
    }
}
