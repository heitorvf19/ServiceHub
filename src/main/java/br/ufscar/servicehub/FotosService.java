package br.ufscar.servicehub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
class FotosService {
    private final Banco db;
    private final ServicoDAO servicos;

    FotosService(Banco db, ServicoDAO servicos) {
        this.db = db;
        this.servicos = servicos;
    }

    void adicionar(long id, String etapa, List<String> fotos) {
        if (fotos == null || fotos.isEmpty()) return;
        if (db.contar("SELECT COUNT(*) FROM foto WHERE servico_id=? AND etapa=?", id, etapa)
                        + fotos.size()
                > 3) throw Erro.campo("Escolha até três fotos por etapa.");
        for (String f : fotos) {
            if (f == null || f.length() > 1_400_000)
                throw Erro.campo("Cada foto pode ter até 1 MB.");
            int pos = f.indexOf(',');
            if (pos < 0) throw Erro.campo("Foto inválida.");
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(f.substring(pos + 1));
            } catch (IllegalArgumentException ex) {
                throw Erro.campo("Foto inválida.");
            }
            boolean jpeg =
                    bytes.length > 3
                            && (bytes[0] & 255) == 255
                            && (bytes[1] & 255) == 216
                            && (bytes[2] & 255) == 255;
            boolean png =
                    bytes.length > 8
                            && java.nio.ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL;
            if ((!jpeg && !png) || bytes.length > 1_048_576)
                throw Erro.campo("Use uma foto JPEG ou PNG de até 1 MB.");
            db.alterar(
                    "INSERT INTO foto(servico_id,etapa,mime,conteudo) VALUES(?,?,?,?)",
                    id,
                    etapa,
                    png ? "image/png" : "image/jpeg",
                    bytes);
        }
    }

    @Transactional
    public void adicionarExecucao(long id, long usuario, List<String> fotos) {
        var v = servicos.bloquear(id);
        v.exigirProfissional(usuario);
        v.exigirEstado("EM_ANDAMENTO");
        adicionar(id, "EXECUCAO", fotos);
    }

    public Map<String, Object> obter(long id, long foto, long usuario) {
        var v = servicos.vinculo(id);
        var m = db.um("SELECT etapa,mime,conteudo FROM foto WHERE id=? AND servico_id=?", foto, id);
        if (v.titular(usuario) || v.contratado(usuario)) return m;
        if ("PEDIDO".equals(m.get("etapa"))
                && ((v.estado().equals("ABERTA") && servicos.compativel(id, usuario))
                        || db.contar(
                                        "SELECT COUNT(*) FROM proposta WHERE servico_id=? AND"
                                            + " profissional_id=?",
                                        id,
                                        usuario)
                                > 0)) return m;
        throw Erro.ausente();
    }
}
