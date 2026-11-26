package br.ufscar.servicehub;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
class InteracaoController {
    private final InteracaoService interacao;
    private final PagamentoService pagamentos;

    InteracaoController(InteracaoService interacao, PagamentoService pagamentos) {
        this.interacao = interacao;
        this.pagamentos = pagamentos;
    }

    record Texto(String texto) {}

    record Avaliar(Integer nota, String comentario) {}

    record Resultado(String resultado) {}

    @GetMapping("/conversas")
    Object conversas(@RequestAttribute long usuario) {
        return interacao.conversas(usuario);
    }

    @GetMapping("/conversas/{id}")
    Object conversa(@PathVariable long id, @RequestAttribute long usuario) {
        return interacao.conversa(id, usuario);
    }

    @PostMapping("/conversas/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    Object enviar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Texto t) {
        return Map.of("id", interacao.mensagem(id, usuario, t.texto));
    }

    @PostMapping("/servicos/{id}/avaliacoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void avaliar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Avaliar a) {
        interacao.avaliar(id, usuario, a.nota, a.comentario);
    }

    @GetMapping("/servicos/{id}/pagamento")
    Object pagamento(@PathVariable long id, @RequestAttribute long usuario) {
        return pagamentos.consultar(id, usuario);
    }

    @PostMapping("/servicos/{id}/pagamento/tentativas")
    Object cobrar(@PathVariable long id, @RequestAttribute long usuario) {
        return pagamentos.iniciar(id, usuario);
    }

    @PostMapping("/simulador/servicos/{id}/cobrancas/{referencia}")
    Object simular(
            @PathVariable long id,
            @PathVariable UUID referencia,
            @RequestAttribute long usuario,
            @RequestBody Resultado r) {
        return pagamentos.simular(id, usuario, referencia, r.resultado);
    }

    @PostMapping("/provedor/eventos")
    Object evento(
            @RequestHeader(value = "X-Assinatura", required = false) String assinatura,
            @RequestBody ProvedorSimulado.Evento e) {
        return pagamentos.confirmar(e, assinatura);
    }
}
