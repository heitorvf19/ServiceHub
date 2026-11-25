package br.ufscar.servicehub;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
class ContaController {
    private final ContaService contas;
    private final Banco db;

    ContaController(ContaService contas, Banco db) {
        this.contas = contas;
        this.db = db;
    }

    record Email(String email) {}

    record NovaSenha(String codigo, String senha, String confirmacaoSenha) {}

    record Confirmacao(Boolean confirmar) {}

    record Sessao(Object usuario, boolean demonstracao) {}

    @GetMapping("/auth/sessao")
    Sessao sessao(
            HttpServletRequest req,
            @org.springframework.beans.factory.annotation.Value("${servicehub.demo}")
                    boolean demo) {
        var s = req.getSession(false);
        if (s != null
                && s.getAttribute("usuario") instanceof Long id
                && db.contar(
                                "SELECT COUNT(*) FROM usuario WHERE id=? AND versao_sessao=?",
                                id,
                                s.getAttribute("versao"))
                        == 1) return new Sessao(contas.perfil(id, true), demo);
        if (s != null) s.invalidate();
        return new Sessao(null, demo);
    }

    @GetMapping("/categorias")
    Object categorias() {
        return db.listar("SELECT id,nome FROM categoria WHERE ativa ORDER BY id");
    }

    @GetMapping("/comunidade")
    Object comunidade() {
        return db.um(
                """
SELECT (SELECT COUNT(*) FROM usuario WHERE tipo='PROFISSIONAL') AS profissionais,
  (SELECT COUNT(*) FROM solicitacao WHERE estado='CONCLUIDO') AS servicos_concluidos,
  (SELECT ROUND(AVG(nota),1) FROM avaliacao_publicada) AS media
""");
    }

    @PostMapping("/auth/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    Object cadastro(@RequestBody ContaService.Cadastro dados, HttpServletRequest req) {
        if (req.getSession(false) != null && req.getSession(false).getAttribute("usuario") != null)
            throw Erro.conflito("Saia da conta atual antes de criar outra.");
        return Map.of("id", contas.cadastrar(dados));
    }

    @PostMapping("/auth/login")
    Object login(@RequestBody ContaService.Credenciais dados, HttpServletRequest req) {
        var acesso = contas.entrar(dados);
        if (req.getSession(false) != null) req.getSession(false).invalidate();
        var s = req.getSession(true);
        s.setAttribute("usuario", acesso.id());
        s.setAttribute("versao", acesso.versao());
        return contas.perfil(acesso.id(), true);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sair(HttpServletRequest req) {
        if (req.getSession(false) != null) req.getSession(false).invalidate();
    }

    @PostMapping("/auth/recuperacao")
    Object recuperar(@RequestBody Email dados) {
        contas.pedirRecuperacao(dados.email());
        return Map.of("mensagem", "Se o e-mail estiver cadastrado, você receberá as instruções.");
    }

    @PostMapping("/auth/redefinir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void redefinir(@RequestBody NovaSenha d) {
        contas.redefinir(d.codigo, d.senha, d.confirmacaoSenha);
    }

    @GetMapping("/perfil")
    Object perfil(@RequestAttribute long usuario) {
        return contas.perfil(usuario, true);
    }

    @PutMapping("/perfil")
    Object atualizar(@RequestAttribute long usuario, @RequestBody ContaService.Perfil d) {
        return contas.atualizar(usuario, d);
    }

    @GetMapping("/usuarios/{id}")
    Object publico(@PathVariable long id) {
        var p = contas.perfil(id, false);
        p.put("avaliacoes", contas.avaliacoesPublicas(id));
        return p;
    }

    @PostMapping("/simulador/conta-recebedora")
    Object vincular(@RequestAttribute long usuario, @RequestBody Confirmacao d) {
        contas.vincular(usuario, d.confirmar);
        return contas.perfil(usuario, true);
    }
}
