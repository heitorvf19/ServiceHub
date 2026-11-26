package br.ufscar.servicehub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Adaptador local do provedor. A interface pode simular um resultado, mas só o evento assinado
 * liquida a tentativa.
 */
@Component
class ProvedorSimulado {
    record Evento(
            UUID eventoId, UUID referencia, long servicoId, BigDecimal valor, String resultado) {}

    private final byte[] segredo;

    ProvedorSimulado(@Value("${servicehub.provedor-chave:}") String chave) {
        segredo = (chave.isBlank() ? Senhas.segredo() : chave).getBytes(StandardCharsets.UTF_8);
    }

    String assinar(Evento e) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            String payload =
                    e.eventoId
                            + "|"
                            + e.referencia
                            + "|"
                            + e.servicoId
                            + "|"
                            + e.valor.toPlainString()
                            + "|"
                            + e.resultado;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    void verificar(Evento e, String assinatura) {
        if (e.eventoId == null
                || e.referencia == null
                || e.valor == null
                || e.resultado == null
                || assinatura == null
                || !MessageDigest.isEqual(
                        assinar(e).getBytes(StandardCharsets.US_ASCII),
                        assinatura.getBytes(StandardCharsets.US_ASCII))) throw Erro.proibido();
        if (!Set.of("PAGO", "RECUSADO", "EXPIRADO").contains(e.resultado))
            throw Erro.campo("Resultado de pagamento inválido.");
    }
}
