# ServiceHub

Aplicativo da Sprint 1 de Engenharia de Software 1. Clientes publicam pedidos de serviços e comparam propostas. Profissionais encontram chamados, negociam e registram a execução. As telas foram recriadas a partir do protótipo do grupo e usam a mesma API e o mesmo banco H2.

## Rodar a versão pronta

1. Extraia o ZIP inteiro e abra a pasta **ServiceHub**, onde ficam este README e o arquivo **servicehub.jar**.
2. Abra o terminal nessa pasta. Use `java -version` para conferir se há Java 17 ou superior instalado.
3. Execute o comando abaixo e mantenha o terminal aberto.

```bash
java -jar servicehub.jar
```

Abra **[http://localhost:8080](http://localhost:8080)**. Para encerrar, pressione **Ctrl+C** no terminal. Para continuar outro dia, execute o mesmo comando na mesma pasta. O aplicativo cria o banco em `dados/servicehub.mv.db`. As dependências e os recursos visuais já estão no JAR.

Se a porta 8080 estiver ocupada, use `java -jar servicehub.jar --server.port=8081` e abra **[http://localhost:8081](http://localhost:8081)**.

## Contas e roteiro para a apresentação

Na tela **Entrar**, os botões **Entrar como cliente** e **Entrar como profissional** acessam as contas abaixo. Para manter os dois perfis abertos ao mesmo tempo, use uma janela normal e outra anônima, ou dois navegadores diferentes. Cada conta também pode entrar por e-mail e senha.

| Perfil | E-mail | Senha |
| --- | --- | --- |
| Cliente · Maria Santos | cliente@servicehub.test | ServiceHub123 |
| Profissional · Carlos Pereira | profissional@servicehub.test | ServiceHub123 |
| Profissional · Ana Costa | ana@servicehub.test | ServiceHub123 |

O primeiro acesso a um banco vazio cria os dados de demonstração. O pedido **Instalação de chuveiro elétrico** tem uma foto do protótipo e duas propostas para comparar. O contrato **Conserto de torneira** já está agendado para Carlos e seu horário inicial já passou.

**Demonstrar a contratação**

1. Como Maria, abra **Analisar propostas recebidas → Comparar propostas** no pedido de instalação. Compare os preços e os escopos, selecione Carlos e confirme a contratação.
2. Na própria tela, proponha um horário futuro com início e término. Como Carlos, abra o mesmo serviço e aceite o horário. A data preferida do pedido e o horário confirmado do contrato aparecem separadamente.
3. Para demonstrar um pedido desde o início, publique uma nova solicitação como Maria, escolhendo **Eletricista** e **São Carlos**. Como Carlos, abra **Ver chamados** e envie preço, escopo e duração. A conversa fica disponível para os dois.

**Demonstrar execução e pagamento sem aguardar o horário de um pedido novo**

1. Como Carlos, abra **Serviços agendados e ativos → Conserto de torneira → Ver detalhes**. Clique em **Iniciar serviço**. Marque itens do checklist, escreva um relato e clique em **Informar término**.
2. Como Maria, abra o mesmo serviço. Você pode solicitar um ajuste, que devolve o serviço a Carlos, ou confirmar a conclusão. Atualize a página quando a outra pessoa mudar o estado.
3. Abra **Ver pagamento → Prosseguir para pagamento → Aprovar pagamento**. A tela de comprovante registra os R$ 120,00 do contrato. Também é possível simular uma recusa ou expiração e fazer nova tentativa.
4. Cada participante pode avaliar o outro. As duas notas se tornam públicas quando ambos avaliam. Se só um avaliar, sua nota se torna pública ao fim de sete dias. Consulte os serviços concluídos em **Histórico de serviços**.

Para repetir a demonstração com os exemplos iniciais preservando o banco anterior, execute o mesmo JAR a partir de outra pasta vazia. O caminho do banco é relativo à pasta de execução. Encerre a instância anterior antes de usar a mesma porta.

## O que está no projeto

| Fluxo | User Stories | Onde usar |
| --- | --- | --- |
| Conta de cliente ou profissional, login, saída e recuperação | C.01, PA.02, U.01A, U.01B, U.02 | Entrar, Criar conta, Esqueci minha senha |
| Perfil próprio, edição e reputação pública | U.03A, U.03B | Perfil e Ver perfil nos detalhes |
| Publicação com fotos, preferências e localização, edição antes da primeira proposta | C.02, C.04 | Solicitar novo serviço ou Editar solicitação |
| Busca por categoria, bairro e texto, oferta e retirada | PA.03, PA.01, PA.06 | Ver chamados e Minhas propostas |
| Comparação e confirmação do contrato | C.03A, C.03B | Propostas recebidas |
| Conversa por solicitação e profissional | U.04 | Ícone de conversa e botão Chat nos detalhes |
| Proposta, aceite, recusa e reagendamento | U.05A, U.05B | Agendamento dentro do serviço |
| Início, checklist, fotos, relato final, ajuste e conclusão | PA.04, PA.05, C.05 | Execução do serviço |
| Acompanhamento, histórico, cancelamento e avaliações mútuas | U.06, U.08, U.07 | Serviços ativos e Histórico |
| Conta recebedora e pagamento simulado com comprovante | PA.07, C.06 | Perfil do profissional e Pagamento do serviço |

### Decisões da versão acadêmica

- O **documento corrigido** orienta preço, privacidade e estados. O pagamento usa o valor contratado, sem taxa, e só fica disponível após a confirmação de conclusão pelo cliente. O endereço completo fica disponível ao cliente e ao profissional contratado. Os demais profissionais veem a cidade e o bairro.
- A quarta etapa do formulário revisa os dados da solicitação. O pagamento tem sua própria tela no fim do serviço. Os números da comunidade vêm do banco de dados.
- O **provedor de pagamento é simulado**. O navegador abre a tela do provedor e escolhe um resultado fictício. O backend verifica o evento, o valor, a referência e o contrato antes de registrar o pagamento. Cada tentativa tem identificação própria e a confirmação repetida preserva o comprovante.
- A **entrega de e-mail é local**. Solicitar recuperação cria uma mensagem em `dados/caixa-email-demo/` para uma conta cadastrada. Abra o arquivo mais recente, copie o trecho que começa com `/#redefinir/` e acrescente-o ao endereço do aplicativo. O link dura 30 minutos, vale para um único uso e um novo pedido substitui o anterior. A troca de senha encerra as sessões anteriores.
- O servidor inicia em HTTP no endereço de loopback para a apresentação em um computador. Os testes incluídos cobrem as regras de negócio. Publicação na internet, homologação nos dispositivos do professor, metas de carga e rotinas operacionais de backup ficam fora desta demonstração local.

## Organização do código

| Caminho | Responsabilidade |
| --- | --- |
| `src/main/resources/static/` | Frontend em HTML, CSS e JavaScript, servido pelo próprio Spring Boot |
| `static/core.js` | Acesso à API, formatação em reais e horário de São Paulo, formulários e confirmações |
| `static/contas.js`, `pedidos.js`, `servico.js` | Telas de conta, publicação, proposta, contratação e acompanhamento |
| `src/main/java/br/ufscar/servicehub/` | Controllers da API, serviços com regras de negócio e acesso ao banco |
| `ServicoDAO.java` e `Banco.java` | Vínculo dos participantes, bloqueios, histórico e SQL parametrizado via Spring JDBC |
| `src/main/resources/schema.sql` | Tabelas e restrições para usuários, solicitações, propostas, contratos, agendas, conversas, avaliações e pagamentos |
| `DadosDemonstracao.java` | Contas e serviços que aparecem na primeira execução |
| `src/test/java/br/ufscar/servicehub/FluxosTest.java` | Testes de integração com H2 e requisições à API |

Por exemplo, em **PA.05**, o formulário da tela envia o relato para `ExecucaoController.terminar`. O método delega a `ExecucaoService.informarTermino`, que valida o profissional e o estado atual. O serviço grava a transição e o relato na mesma transação e a tela mostra **Aguardando confirmação**. O cliente usa uma ação própria para confirmar ou pedir ajuste.

## Editar, testar e recompilar

Abra no VS Code ou no IntelliJ a pasta que contém o `pom.xml`. Para compilar a partir dos fontes, use um **JDK 17 ou superior e Maven 3.6.3 ou superior**. O Maven baixa as dependências na primeira compilação.

```bash
mvn clean package
java -jar target/servicehub.jar
```

O primeiro comando compila, roda os testes e gera o novo executável em `target/`. O comando `mvn spring-boot:run` inicia a aplicação a partir do projeto. Para executar apenas os testes, use `mvn test`. Os testes usam um H2 em memória, separado do banco da apresentação. Para iniciar um banco vazio sem as contas de demonstração, execute o JAR em uma pasta nova com `--servicehub.demo=false`.

## Integração entre as telas e a API

As telas enviam JSON para rotas em `/api`, na mesma origem. O servidor identifica o usuário pelo cookie de sessão criado no login. O identificador em `/servicos/{id}` acompanha a solicitação do início ao fim, inclusive depois da contratação. Datas de API usam ISO 8601 com fuso. Valores monetários usam reais com até duas casas decimais.

| Ação | Método e rota |
| --- | --- |
| Cadastro, login e saída | `POST /api/auth/cadastro`, `/api/auth/login`, `/api/auth/logout` |
| Catálogo e perfil | `GET /api/categorias`, `GET` ou `PUT /api/perfil` |
| Publicação | `POST /api/servicos` com cabeçalho `Idempotency-Key`. Reuse a chave ao reenviar a mesma publicação |
| Chamados, serviços próprios e detalhes | `GET /api/oportunidades`, `/api/servicos`, `/api/servicos/{id}` |
| Envio e comparação | `POST` ou `GET /api/servicos/{id}/propostas` |
| Contratar ou retirar | `POST /api/servicos/{id}/propostas/{proposta}/contratacao` ou `/retirada` |
| Propor ou responder horário | `POST /api/servicos/{id}/horarios`, `POST /api/servicos/{id}/horarios/{horario}/resposta` |
| Execução e conclusão | `POST /api/servicos/{id}/inicio`, `/termino`, `/ajustes`, `/conclusao` |
| Conversas e mensagens | `GET /api/conversas`, `GET /api/conversas/{id}`, `POST /api/conversas/{id}/mensagens` |
| Pagamento | `GET /api/servicos/{id}/pagamento`, `POST /api/servicos/{id}/pagamento/tentativas` |
| Avaliar ou cancelar | `POST /api/servicos/{id}/avaliacoes` ou `/cancelamento` |

Nos formulários, os erros da API aparecem como uma mensagem em português. As chamadas com sessão expirada retornam 401. Restrições de perfil ou titularidade retornam 403 ou 404 e conflitos de estado retornam 409.
