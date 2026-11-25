package br.ufscar.servicehub;

import jakarta.servlet.http.*;

import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

import java.net.URI;
import java.util.Map;

@Configuration
class WebConfig implements WebMvcConfigurer {
    private final Banco db;

    WebConfig(Banco db) {
        this.db = db;
    }

    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(
                        new HandlerInterceptor() {
                            @Override
                            public boolean preHandle(
                                    HttpServletRequest req,
                                    HttpServletResponse res,
                                    Object handler) {
                                res.setHeader("Cache-Control", "no-store");
                                res.setHeader("X-Content-Type-Options", "nosniff");
                                String origem = req.getHeader("Origin");
                                if (!req.getMethod().equals("GET") && origem != null) {
                                    URI url;
                                    try {
                                        url = URI.create(origem);
                                    } catch (IllegalArgumentException ex) {
                                        throw Erro.proibido();
                                    }
                                    if (!req.getScheme().equalsIgnoreCase(url.getScheme())
                                            || !req.getHeader("Host")
                                                    .equalsIgnoreCase(url.getRawAuthority()))
                                        throw Erro.proibido();
                                }
                                return true;
                            }
                        })
                .addPathPatterns("/api/**");
        registro.addInterceptor(
                        new HandlerInterceptor() {
                            @Override
                            public boolean preHandle(
                                    HttpServletRequest req,
                                    HttpServletResponse res,
                                    Object handler) {
                                var sessao = req.getSession(false);
                                if (sessao != null
                                        && sessao.getAttribute("usuario") instanceof Long id
                                        && db.contar(
                                                        "SELECT COUNT(*) FROM usuario WHERE id=?"
                                                            + " AND versao_sessao=?",
                                                        id,
                                                        sessao.getAttribute("versao"))
                                                == 1) {
                                    req.setAttribute("usuario", id);
                                    return true;
                                }
                                if (sessao != null) sessao.invalidate();
                                throw new Erro(401, "Entre na conta para continuar.");
                            }
                        })
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/categorias",
                        "/api/comunidade",
                        "/api/provedor/eventos");
    }
}

@RestControllerAdvice
class TratamentoErros {
    @ExceptionHandler(Erro.class)
    ResponseEntity<?> negocio(Erro e) {
        return ResponseEntity.status(e.status).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> formato(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("mensagem", "Confira o formato dos campos enviados."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflito(DataIntegrityViolationException e) {
        return ResponseEntity.status(409)
                .body(Map.of("mensagem", "O registro já existe ou mudou. Atualize a tela."));
    }
}
