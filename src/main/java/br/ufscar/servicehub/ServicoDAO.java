package br.ufscar.servicehub;

import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.*;

@Repository
public class ServicoDAO {
    private final Banco db;
    private final Clock clock;

    ServicoDAO(Banco db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    record Vinculo(long id, long cliente, Long profissional, String estado) {
        boolean titular(long usuario) {
            return cliente == usuario;
        }

        boolean contratado(long usuario) {
            return profissional != null && profissional == usuario;
        }

        void exigirCliente(long usuario) {
            if (!titular(usuario)) throw Erro.proibido();
        }

        void exigirProfissional(long usuario) {
            if (!contratado(usuario)) throw Erro.proibido();
        }

        void exigirParticipante(long usuario) {
            if (!titular(usuario) && !contratado(usuario)) throw Erro.ausente();
        }

        void exigirEstado(String... estados) {
            if (!Arrays.asList(estados).contains(estado))
                throw Erro.conflito("Estado atual do serviço: " + estado + ".");
        }
    }

    Vinculo vinculo(long id) {
        var r =
                db.um(
                        "SELECT s.cliente_id,s.estado,c.profissional_id FROM solicitacao s LEFT"
                            + " JOIN contrato c ON s.id=c.servico_id WHERE s.id=?",
                        id);
        return new Vinculo(
                id,
                (long) r.get("clienteId"),
                (Long) r.get("profissionalId"),
                (String) r.get("estado"));
    }

    Vinculo bloquear(long id) {
        db.um("SELECT id FROM solicitacao WHERE id=? FOR UPDATE", id);
        return vinculo(id);
    }

    boolean compativel(long id, long usuario) {
        return db.contar(
                        """
SELECT COUNT(*) FROM solicitacao s JOIN atuacao a ON s.categoria_id=a.categoria_id
JOIN usuario u ON u.id=a.profissional_id
WHERE s.id=? AND u.id=? AND LOWER(s.cidade)=LOWER(u.cidade)
""",
                        id,
                        usuario)
                > 0;
    }

    boolean repetida(UUID operacao, long id, long usuario, String evento) {
        if (operacao == null) return false;
        var anterior =
                db.opcional(
                        "SELECT servico_id,autor_id,evento FROM historico WHERE operacao=?",
                        operacao);
        if (anterior == null) return false;
        if (!Objects.equals(anterior.get("servicoId"), id)
                || !Objects.equals(anterior.get("autorId"), usuario)
                || !evento.equals(anterior.get("evento")))
            throw Erro.conflito("Identificador de operação já utilizado.");
        return true;
    }

    void registrar(
            long id,
            Long autor,
            String evento,
            String origem,
            String destino,
            String detalhe,
            UUID operacao) {
        db.alterar(
                "INSERT INTO"
                    + " historico(servico_id,autor_id,evento,origem,destino,detalhe,operacao,data_hora)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                id,
                autor,
                evento,
                origem,
                destino,
                detalhe,
                operacao == null ? UUID.randomUUID() : operacao,
                clock.instant());
        db.alterar("UPDATE solicitacao SET atualizada_em=? WHERE id=?", clock.instant(), id);
    }

    void transicao(
            Vinculo v, long autor, String evento, String destino, String detalhe, UUID operacao) {
        db.alterar("UPDATE solicitacao SET estado=? WHERE id=?", destino, v.id);
        registrar(v.id, autor, evento, v.estado, destino, detalhe, operacao);
    }
}
