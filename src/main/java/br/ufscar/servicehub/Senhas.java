package br.ufscar.servicehub;

import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Senhas {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERACOES = 210_000;

    private Senhas() {}

    static String segredo() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String senha) {
        byte[] sal = new byte[16];
        RANDOM.nextBytes(sal);
        return ITERACOES
                + ":"
                + Base64.getEncoder().encodeToString(sal)
                + ":"
                + Base64.getEncoder().encodeToString(derivar(senha, sal, ITERACOES));
    }

    static boolean verifica(String senha, String salvo) {
        String[] partes = salvo.split(":");
        return MessageDigest.isEqual(
                Base64.getDecoder().decode(partes[2]),
                derivar(senha, Base64.getDecoder().decode(partes[1]), Integer.parseInt(partes[0])));
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        } finally {
            spec.clearPassword();
        }
    }

    static String digest(String valor) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            valor.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
