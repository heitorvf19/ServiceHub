package br.ufscar.servicehub;

class Erro extends RuntimeException {
    final int status;

    Erro(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    static Erro campo(String mensagem) {
        return new Erro(400, mensagem);
    }

    static Erro proibido() {
        return new Erro(403, "Seu perfil não pode executar esta ação.");
    }

    static Erro ausente() {
        return new Erro(404, "Registro não encontrado ou sem acesso.");
    }

    static Erro conflito(String mensagem) {
        return new Erro(409, mensagem);
    }
}
