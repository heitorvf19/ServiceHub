package br.ufscar.servicehub;

import java.math.BigDecimal;
import java.util.Locale;

final class Validar {
    private Validar() {}

    static String texto(String valor, int min, int max, String nome) {
        String s = valor == null ? "" : valor.strip();
        if (s.length() < min || s.length() > max)
            throw Erro.campo(nome + " deve ter entre " + min + " e " + max + " caracteres.");
        return s;
    }

    static String opcional(String valor, int max, String nome) {
        return valor == null || valor.isBlank() ? null : texto(valor, 1, max, nome);
    }

    static String email(String valor) {
        var email = texto(valor, 3, 254, "E-mail").toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw Erro.campo("Informe um e-mail válido.");
        return email;
    }

    static void confirmar(Boolean sim) {
        if (!Boolean.TRUE.equals(sim)) throw Erro.campo("É preciso confirmar esta operação.");
    }

    static BigDecimal decimal(BigDecimal valor, int inteiros, boolean positivo, String nome) {
        if (valor == null
                || valor.signum() < 0
                || (positivo && valor.signum() == 0)
                || valor.stripTrailingZeros().scale() > 2
                || valor.compareTo(BigDecimal.TEN.pow(inteiros)) >= 0)
            throw Erro.campo(nome + " inválido. Use até duas casas decimais.");
        return valor.setScale(2);
    }
}
