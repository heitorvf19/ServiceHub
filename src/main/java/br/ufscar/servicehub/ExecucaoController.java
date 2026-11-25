package br.ufscar.servicehub;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/servicos/{id}")
class ExecucaoController {
    private final AgendamentoService agenda;
    private final ExecucaoService execucao;

    ExecucaoController(AgendamentoService agenda, ExecucaoService execucao) {
        this.agenda = agenda;
        this.execucao = execucao;
    }

    record Horario(Instant inicio, Instant fim) {}

    record Resposta(Boolean aceitar) {}

    record Comando(Boolean confirmar, String texto, UUID operacaoId) {}

    record Item(Boolean marcado) {}

    @PostMapping("/horarios")
    @ResponseStatus(HttpStatus.CREATED)
    Object propor(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Horario h) {
        return Map.of("id", agenda.propor(id, usuario, h.inicio, h.fim));
    }

    @PostMapping("/horarios/{h}/resposta")
    Object responder(
            @PathVariable long id,
            @PathVariable long h,
            @RequestAttribute long usuario,
            @RequestBody Resposta r) {
        return agenda.responder(id, h, usuario, r.aceitar);
    }

    @PostMapping("/inicio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void iniciar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Comando c) {
        execucao.iniciarExecucao(id, usuario, c.confirmar, c.operacaoId);
    }

    @PutMapping("/checklist/{item}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void item(
            @PathVariable long id,
            @PathVariable int item,
            @RequestAttribute long usuario,
            @RequestBody Item c) {
        execucao.checklist(id, usuario, item, c.marcado);
    }

    @PostMapping("/termino")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void terminar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Comando c) {
        execucao.informarTermino(id, usuario, c.texto, c.confirmar, c.operacaoId);
    }

    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ajustar(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Comando c) {
        execucao.solicitarAjuste(id, usuario, c.texto, c.operacaoId);
    }

    @PostMapping("/conclusao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void concluir(@PathVariable long id, @RequestAttribute long usuario, @RequestBody Comando c) {
        execucao.confirmarConclusao(id, usuario, c.confirmar, c.operacaoId);
    }
}
