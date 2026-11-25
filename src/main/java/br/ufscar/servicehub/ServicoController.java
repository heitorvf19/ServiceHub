package br.ufscar.servicehub;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
class ServicoController {
    private final SolicitacaoService pedidos;
    private final PropostaService propostas;
    private final FotosService fotos;

    ServicoController(SolicitacaoService pedidos, PropostaService propostas, FotosService fotos) {
        this.pedidos = pedidos;
        this.propostas = propostas;
        this.fotos = fotos;
    }

    public record Acao(Boolean confirmar, String motivo, UUID operacaoId) {}

    public record Fotos(List<String> fotos) {}

    @GetMapping("/oportunidades")
    Object oportunidades(
            @RequestAttribute long usuario,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) String texto) {
        return pedidos.buscar(usuario, categoria, bairro, texto);
    }

    @GetMapping("/servicos")
    Object meus(
            @RequestAttribute long usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate) {
        return pedidos.meus(usuario, estado, de, ate);
    }

    @PostMapping("/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    Object publicar(
            @RequestAttribute long usuario,
            @RequestHeader("Idempotency-Key") String chave,
            @RequestBody SolicitacaoService.Pedido d) {
        return Map.of("id", pedidos.publicar(usuario, chave, d));
    }

    @GetMapping("/servicos/{id}")
    Object detalhes(@PathVariable long id, @RequestAttribute long usuario) {
        return pedidos.detalhes(id, usuario);
    }

    @PutMapping("/servicos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editar(
            @PathVariable long id,
            @RequestAttribute long usuario,
            @RequestBody SolicitacaoService.Pedido d) {
        pedidos.editar(id, usuario, d);
    }

    @PostMapping("/servicos/{id}/cancelamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Acao d) {
        pedidos.cancelar(id, usuario, d.motivo, d.confirmar, d.operacaoId);
    }

    @GetMapping("/servicos/{id}/fotos/{foto}")
    ResponseEntity<byte[]> foto(
            @PathVariable long id, @PathVariable long foto, @RequestAttribute long usuario) {
        var f = fotos.obter(id, foto, usuario);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) f.get("mime")))
                .body((byte[]) f.get("conteudo"));
    }

    @PostMapping("/servicos/{id}/fotos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void fotos(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Fotos d) {
        fotos.adicionarExecucao(id, usuario, d.fotos);
    }

    @GetMapping("/propostas/minhas")
    Object minhas(@RequestAttribute long usuario) {
        return propostas.minhas(usuario);
    }

    @GetMapping("/servicos/{id}/propostas")
    Object comparar(@PathVariable long id, @RequestAttribute long usuario) {
        return propostas.comparar(id, usuario);
    }

    @PostMapping("/servicos/{id}/propostas")
    @ResponseStatus(HttpStatus.CREATED)
    Object enviar(
            @PathVariable long id,
            @RequestAttribute long usuario,
            @RequestBody PropostaService.Oferta d) {
        return Map.of("id", propostas.enviar(id, usuario, d));
    }

    @PostMapping("/servicos/{id}/propostas/{p}/retirada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirar(
            @PathVariable long id,
            @PathVariable long p,
            @RequestAttribute long usuario,
            @RequestBody Acao d) {
        propostas.retirar(id, p, usuario, d.confirmar);
    }

    @PostMapping("/servicos/{id}/propostas/{p}/contratacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void contratar(
            @PathVariable long id,
            @PathVariable long p,
            @RequestAttribute long usuario,
            @RequestBody Acao d) {
        propostas.contratar(id, p, usuario, d.confirmar, d.operacaoId);
    }
}
