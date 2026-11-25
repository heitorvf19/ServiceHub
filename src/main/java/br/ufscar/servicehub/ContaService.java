package br.ufscar.servicehub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class ContaService {
    public record Cadastro(
            String tipo,
            String nome,
            String email,
            String telefone,
            String senha,
            String confirmacaoSenha,
            String apresentacao,
            String cidade,
            String bairros,
            List<Long> categorias) {}

    public record Perfil(
            String nome,
            String telefone,
            String apresentacao,
            String cidade,
            String bairros,
            List<Long> categorias) {}

    public record Credenciais(String email, String senha) {}

    public record Acesso(long id, int versao) {}

    private final Banco db;
    private final Clock clock;
    private final Path caixaEmail;
    private final String hashFicticio = Senhas.hash(Senhas.segredo());

    ContaService(Banco db, Clock clock, @Value("${servicehub.caixa-email}") String caixaEmail) {
        this.db = db;
        this.clock = clock;
        this.caixaEmail = Path.of(caixaEmail);
    }

    @Transactional
    public long cadastrar(Cadastro c) {
        if (!"CLIENTE".equals(c.tipo) && !"PROFISSIONAL".equals(c.tipo))
            throw Erro.campo("Escolha o tipo de conta.");
        var email = Validar.email(c.email);
        validarSenha(c.senha, c.confirmacaoSenha);
        if (db.contar("SELECT COUNT(*) FROM usuario WHERE email=?", email) > 0)
            throw Erro.conflito("E-mail já cadastrado. Entre na conta ou recupere sua senha.");
        var p =
                conferirPerfil(
                        new Perfil(
                                c.nome,
                                c.telefone,
                                c.apresentacao,
                                c.cidade,
                                c.bairros,
                                c.categorias),
                        c.tipo);
        long id =
                db.inserir(
                        "INSERT INTO"
                            + " usuario(tipo,nome,email,telefone,senha_hash,apresentacao,cidade,bairros)"
                            + " VALUES(?,?,?,?,?,?,?,?)",
                        c.tipo,
                        p.nome,
                        email,
                        p.telefone,
                        Senhas.hash(c.senha),
                        p.apresentacao,
                        p.cidade,
                        p.bairros);
        salvarCategorias(id, p.categorias);
        return id;
    }

    private Perfil conferirPerfil(Perfil p, String tipo) {
        String nome = Validar.texto(p.nome, 3, 100, "Nome completo");
        if (nome.split("\\s+").length < 2) throw Erro.campo("Informe nome e sobrenome.");
        String fone = Validar.texto(p.telefone, 8, 25, "Telefone");
        if (!fone.matches("[+()0-9 .-]+") || fone.replaceAll("\\D", "").length() < 8)
            throw Erro.campo("Informe um telefone válido.");
        if ("CLIENTE".equals(tipo)) return new Perfil(nome, fone, null, null, null, List.of());
        if (p.categorias == null || p.categorias.isEmpty())
            throw Erro.campo("Selecione ao menos uma categoria.");
        var categorias = new ArrayList<>(new LinkedHashSet<>(p.categorias));
        for (Long categoria : categorias) categoriaAtiva(categoria);
        return new Perfil(
                nome,
                fone,
                Validar.texto(p.apresentacao, 20, 1000, "Apresentação"),
                Validar.texto(p.cidade, 1, 100, "Cidade"),
                Validar.opcional(p.bairros, 400, "Bairros"),
                categorias);
    }

    private void salvarCategorias(long id, List<Long> categorias) {
        db.alterar("DELETE FROM atuacao WHERE profissional_id=?", id);
        for (long categoria : categorias)
            db.alterar("INSERT INTO atuacao VALUES(?,?)", id, categoria);
    }

    void categoriaAtiva(Long id) {
        if (id == null || db.contar("SELECT COUNT(*) FROM categoria WHERE id=? AND ativa", id) == 0)
            throw Erro.campo("Escolha uma categoria do catálogo.");
    }

    private void validarSenha(String s, String confirmacao) {
        if (s == null || s.length() < 8 || s.length() > 128)
            throw Erro.campo("A senha deve ter de 8 a 128 caracteres.");
        if (!s.equals(confirmacao)) throw Erro.campo("As senhas digitadas precisam coincidir.");
    }

    @Transactional(noRollbackFor = FalhaLogin.class)
    public Acesso entrar(Credenciais dados) {
        var email = Validar.email(dados.email);
        db.alterar("MERGE INTO acesso(email) KEY(email) VALUES(?)", email);
        var limite = db.um("SELECT * FROM acesso WHERE email=? FOR UPDATE", email);
        var agora = clock.instant();
        var bloqueio = (Instant) limite.get("bloqueioAte");
        if (bloqueio != null && bloqueio.isAfter(agora))
            throw new FalhaLogin(429, "Muitas tentativas. Aguarde 15 minutos.");
        var conta =
                db.opcional("SELECT id,versao_sessao,senha_hash FROM usuario WHERE email=?", email);
        String senha = dados.senha == null || dados.senha.length() > 128 ? "" : dados.senha;
        boolean valida =
                Senhas.verifica(
                        senha, conta == null ? hashFicticio : (String) conta.get("senhaHash"));
        if (!valida || conta == null) {
            var inicio = (Instant) limite.get("primeiraFalha");
            int falhas = (int) limite.get("falhas");
            if (inicio == null || !inicio.plusSeconds(900).isAfter(agora) || bloqueio != null) {
                inicio = agora;
                falhas = 0;
            }
            falhas++;
            db.alterar(
                    "UPDATE acesso SET falhas=?,primeira_falha=?,bloqueio_ate=? WHERE email=?",
                    falhas,
                    inicio,
                    falhas >= 5 ? agora.plusSeconds(900) : null,
                    email);
            if (falhas >= 5) throw new FalhaLogin(429, "Muitas tentativas. Aguarde 15 minutos.");
            throw new FalhaLogin(401, "E-mail ou senha inválidos.");
        }
        db.alterar(
                "UPDATE acesso SET falhas=0,primeira_falha=NULL,bloqueio_ate=NULL WHERE email=?",
                email);
        return new Acesso((long) conta.get("id"), (int) conta.get("versaoSessao"));
    }

    private static class FalhaLogin extends Erro {
        FalhaLogin(int status, String m) {
            super(status, m);
        }
    }

    public Map<String, Object> perfil(long id, boolean proprio) {
        var perfil =
                db.um(
                        "SELECT id,nome,tipo,apresentacao,cidade,bairros FROM usuario WHERE id=?",
                        id);
        perfil.put(
                "categorias",
                db.listar(
                        "SELECT c.id,c.nome FROM categoria c JOIN atuacao a ON c.id=a.categoria_id"
                            + " WHERE a.profissional_id=? ORDER BY c.id",
                        id));
        perfil.putAll(reputacao(id));
        if (proprio)
            perfil.putAll(
                    db.um(
                            "SELECT email,telefone,(recebedor IS NOT NULL) AS conta_vinculada FROM"
                                + " usuario WHERE id=?",
                            id));
        else {
            perfil.remove("cidade");
            perfil.remove("bairros");
        }
        return perfil;
    }

    public Map<String, Object> reputacao(long id) {
        return db.um(
                "SELECT ROUND(AVG(nota),1) AS media,COUNT(*) AS total_avaliacoes FROM"
                    + " avaliacao_publicada WHERE avaliado_id=?",
                id);
    }

    public List<Map<String, Object>> avaliacoesPublicas(long id) {
        return db.listar(
                "SELECT a.nota,a.comentario,a.criada_em,u.nome AS autor FROM avaliacao_publicada a"
                    + " JOIN usuario u ON u.id=a.autor_id WHERE a.avaliado_id=? ORDER BY a.id DESC",
                id);
    }

    public void exigirTipo(long id, String tipo) {
        if (db.contar("SELECT COUNT(*) FROM usuario WHERE id=? AND tipo=?", id, tipo) == 0)
            throw Erro.proibido();
    }

    @Transactional
    public Map<String, Object> atualizar(long id, Perfil dados) {
        var u = db.um("SELECT tipo FROM usuario WHERE id=? FOR UPDATE", id);
        var p = conferirPerfil(dados, (String) u.get("tipo"));
        db.alterar(
                "UPDATE usuario SET nome=?,telefone=?,apresentacao=?,cidade=?,bairros=? WHERE id=?",
                p.nome,
                p.telefone,
                p.apresentacao,
                p.cidade,
                p.bairros,
                id);
        salvarCategorias(id, p.categorias);
        return perfil(id, true);
    }

    @Transactional
    public void vincular(long id, Boolean confirmado) {
        exigirTipo(id, "PROFISSIONAL");
        Validar.confirmar(confirmado);
        db.alterar(
                "UPDATE usuario SET recebedor=COALESCE(recebedor,?) WHERE id=?",
                "demo-" + UUID.randomUUID(),
                id);
    }

    @Transactional
    public void pedirRecuperacao(String entrada) {
        String email = Validar.email(entrada);
        var conta = db.opcional("SELECT id FROM usuario WHERE email=? FOR UPDATE", email);
        if (conta == null) return;
        String codigo = Senhas.segredo();
        db.alterar(
                "MERGE INTO recuperacao(usuario_id,codigo_hash,validade) KEY(usuario_id)"
                    + " VALUES(?,?,?)",
                conta.get("id"),
                Senhas.digest(codigo),
                clock.instant().plusSeconds(1800));
        try {
            Files.createDirectories(caixaEmail);
            String texto =
                    "Para: "
                            + email
                            + "\nServiceHub · Recuperação de senha\n"
                            + "Abra o endereço do ServiceHub e acrescente ao final:\n/#redefinir/"
                            + codigo
                            + "\nValidade: 30 minutos.\n";
            Files.writeString(
                    caixaEmail.resolve("mensagem-" + UUID.randomUUID() + ".txt"),
                    texto,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new Erro(503, "Não foi possível entregar a mensagem de recuperação.");
        }
    }

    @Transactional
    public void redefinir(String codigo, String senha, String confirmacao) {
        validarSenha(senha, confirmacao);
        String hash = Senhas.digest(Validar.texto(codigo, 40, 100, "Código"));
        var ref = db.opcional("SELECT usuario_id FROM recuperacao WHERE codigo_hash=?", hash);
        if (ref == null) throw Erro.campo("Link inválido ou expirado.");
        var id = ref.get("usuarioId");
        db.um("SELECT id FROM usuario WHERE id=? FOR UPDATE", id);
        if (db.alterar(
                        "DELETE FROM recuperacao WHERE usuario_id=? AND codigo_hash=? AND"
                            + " validade>?",
                        id,
                        hash,
                        clock.instant())
                != 1) throw Erro.campo("Link inválido ou expirado.");
        db.alterar(
                "UPDATE usuario SET senha_hash=?,versao_sessao=versao_sessao+1 WHERE id=?",
                Senhas.hash(senha),
                id);
    }
}
