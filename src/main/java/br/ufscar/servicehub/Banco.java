package br.ufscar.servicehub;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Consultas parametrizadas. As projeções SQL definem quais campos cada tela recebe. */
@Repository
public class Banco {
    private final JdbcTemplate jdbc;

    public Banco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int alterar(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    public long inserir(String sql, Object... args) {
        var chave = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    var stmt = con.prepareStatement(sql, new String[] {"ID"});
                    for (int i = 0; i < args.length; i++) stmt.setObject(i + 1, args[i]);
                    return stmt;
                },
                chave);
        return Objects.requireNonNull(chave.getKey()).longValue();
    }

    public List<Map<String, Object>> listar(String sql, Object... args) {
        return jdbc.query(
                sql,
                (rs, numero) -> {
                    Map<String, Object> linha = new LinkedHashMap<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object valor = rs.getObject(i);
                        if (valor instanceof OffsetDateTime d) valor = d.toInstant();
                        else if (valor instanceof Timestamp d) valor = d.toInstant();
                        else if (valor instanceof Blob b) valor = b.getBytes(1, (int) b.length());
                        linha.put(camel(rs.getMetaData().getColumnLabel(i)), valor);
                    }
                    return linha;
                },
                args);
    }

    public Map<String, Object> opcional(String sql, Object... args) {
        var linhas = listar(sql, args);
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    public Map<String, Object> um(String sql, Object... args) {
        var linha = opcional(sql, args);
        if (linha == null) throw Erro.ausente();
        return linha;
    }

    private static String camel(String coluna) {
        var nome = new StringBuilder();
        boolean maiuscula = false;
        for (char c : coluna.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                maiuscula = true;
                continue;
            }
            nome.append(maiuscula ? Character.toUpperCase(c) : c);
            maiuscula = false;
        }
        return nome.toString();
    }
}
