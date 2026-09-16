# DBEduca — Execução Controlada no Oracle

Data: 2026-09-16
Branch: `feat/sprint-2-database-execution`
Status: design aprovado em conversa; aguardando revisão final do documento antes do plano de implementação.

## 1. Objetivo

Adicionar ao DBEduca a primeira execução real de banco de dados a partir do fluxo já existente de modelagem e geração de scripts.

Neste incremento, a execução será limitada ao Oracle e somente para DDL gerado pelo próprio backend a partir da estrutura definida pelo aluno. Não haverá editor de SQL livre nesta etapa.

Fluxo pedagógico:

```text
Aluno modela a tabela
        ↓
DBEduca gera o SQL Oracle
        ↓
Aluno revisa o script
        ↓
Clica em “Executar no Oracle”
        ↓
Backend reconstrói e valida o modelo
        ↓
Backend gera novamente o DDL Oracle
        ↓
OracleExecutionAdapter executa via JDBC
        ↓
Resultado amigável é exibido na interface
```

## 2. Escopo

### Incluído

- execução controlada de `CREATE TABLE` no Oracle;
- uso do `OracleScriptGenerator` existente como fonte do DDL;
- conexão JDBC do backend com o Oracle;
- endpoint REST dedicado à execução;
- detecção prévia de tabela existente;
- retorno HTTP `409 Conflict` quando a tabela já existir;
- tratamento de indisponibilidade do Oracle;
- botão `Executar no Oracle` no frontend;
- feedback visual de sucesso e erro;
- configuração por variáveis de ambiente;
- testes unitários, MVC e integração manual com o container Oracle local.

### Fora do escopo

- SQL livre digitado pelo usuário;
- `DROP TABLE` automático;
- exclusão ou recriação automática de objetos existentes;
- execução de `ALTER`, `TRUNCATE`, `GRANT`, `REVOKE` ou comandos administrativos;
- execução de `INSERT`, `UPDATE`, `DELETE` ou `SELECT` pela interface neste incremento;
- execução real em PostgreSQL, MySQL ou MongoDB;
- isolamento por aluno/projeto;
- autenticação e autorização;
- histórico persistido de execuções;
- quotas e rate limiting.

Esses itens poderão ser adicionados em incrementos posteriores do Sprint 2 e Sprints seguintes.

## 3. Decisões aprovadas

1. A primeira engine com execução real será Oracle.
2. O navegador não enviará SQL arbitrário para execução.
3. O frontend enviará a mesma estrutura de tabela usada na geração de script.
4. O backend regenerará o DDL antes de executá-lo.
5. Se a tabela já existir, a operação será interrompida e retornará uma mensagem amigável.
6. O sistema não executará `DROP TABLE` automaticamente.
7. Credenciais de banco não serão expostas ao frontend.
8. A conexão com o Oracle será feita via JDBC, não por chamada de `sqlplus` ou processo Docker externo.
9. A arquitetura será extensível para futuros adapters de PostgreSQL, MySQL e MongoDB.

## 4. Arquitetura

```text
┌──────────────────────────────┐
│          React 19            │
│      Modelador DBEduca       │
└──────────────┬───────────────┘
               │ HTTP/JSON
               ▼
┌──────────────────────────────┐
│       ExecutionController    │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│   DatabaseExecutionService   │
│ valida engine e coordena     │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│    DatabaseExecutionAdapter  │
│        contrato comum        │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│     OracleExecutionAdapter   │
│ USER_TABLES + JDBC execute   │
└──────────────┬───────────────┘
               │ JDBC
               ▼
┌──────────────────────────────┐
│ Oracle Free / FREEPDB1       │
│ usuário de aplicação DBEDUCA │
└──────────────────────────────┘
```

O `DatabaseExecutionService` não conhecerá detalhes de JDBC Oracle. O `OracleExecutionAdapter` será responsável pelo acesso ao banco e pela tradução de falhas técnicas para exceções de domínio apropriadas.

## 5. Componentes previstos

### Pacote `execution`

```text
backend/src/main/java/com/dbeduca/execution/
├── DatabaseExecutionAdapter.java
├── DatabaseExecutionService.java
├── ExecutionResult.java
├── TableAlreadyExistsException.java
├── DatabaseUnavailableException.java
└── OracleExecutionAdapter.java
```

### Pacote `api`

```text
backend/src/main/java/com/dbeduca/api/
├── ExecutionController.java
├── ExecuteDatabaseRequest.java
└── ExecuteDatabaseResponse.java
```

### Arquivos existentes a alterar

- `backend/pom.xml` — adicionar suporte JDBC Oracle;
- `backend/src/main/resources/application.properties` — propriedades de conexão derivadas de variáveis de ambiente;
- `backend/src/main/java/com/dbeduca/api/ApiExceptionHandler.java` — mapear conflitos e indisponibilidade;
- `backend/src/main/java/com/dbeduca/config/ApplicationConfig.java` — registrar componentes quando necessário;
- `frontend/src/api.js` — adicionar chamada de execução;
- `frontend/src/App.jsx` — botão e estado da execução;
- `frontend/src/styles.css` — estados visuais de sucesso/erro;
- `docker-compose.yml` — fornecer variáveis de ambiente do Oracle ao backend quando executado em Compose;
- `.env.example` — documentar variáveis necessárias;
- `README.md` — documentar o novo fluxo e a conexão Oracle.

## 6. Contrato REST

### Endpoint

```http
POST /api/v1/executions
Content-Type: application/json
```

### Request

```json
{
  "engine": "ORACLE",
  "tableName": "alunos",
  "columns": [
    {
      "name": "id",
      "type": "BIGINT",
      "primaryKey": true,
      "notNull": true,
      "unique": false
    },
    {
      "name": "nome",
      "type": "VARCHAR(100)",
      "primaryKey": false,
      "notNull": true,
      "unique": false
    }
  ]
}
```

O request será convertido para `TableDefinition` usando as mesmas regras de validação já aplicadas na geração de scripts.

### Resposta de sucesso

```json
{
  "engine": "ORACLE",
  "status": "SUCCESS",
  "objectType": "TABLE",
  "objectName": "ALUNOS",
  "message": "Tabela ALUNOS criada com sucesso no Oracle.",
  "script": "CREATE TABLE alunos (...)"
}
```

O script retornado é o script regenerado no backend e efetivamente encaminhado ao adapter para execução.

### Resposta quando a tabela já existe

HTTP `409 Conflict`:

```json
{
  "message": "A tabela ALUNOS já existe no Oracle.",
  "engine": "ORACLE"
}
```

### Engine não habilitada para execução

Enquanto somente Oracle estiver disponível para execução, outras engines deverão ser rejeitadas no endpoint com HTTP `400 Bad Request` e mensagem clara informando que a execução ainda não está habilitada para aquela engine.

### Oracle indisponível

HTTP `503 Service Unavailable`:

```json
{
  "message": "Oracle temporariamente indisponível. Verifique o ambiente do laboratório.",
  "engine": "ORACLE"
}
```

Detalhes JDBC ou mensagens sensíveis do driver não serão enviados ao navegador.

## 7. Fluxo interno de execução

1. `ExecutionController` recebe e valida o request.
2. `ExecuteDatabaseRequest` produz uma `TableDefinition` validada.
3. `DatabaseExecutionService` confirma que `ORACLE` está habilitado para execução.
4. O serviço obtém o DDL do `ScriptGeneratorRegistry` para `DatabaseEngine.ORACLE`.
5. O serviço delega ao `OracleExecutionAdapter` o nome da tabela e o DDL regenerado.
6. O adapter consulta `USER_TABLES` usando parâmetro bind para verificar existência da tabela.
7. Se existir, lança `TableAlreadyExistsException` antes de qualquer DDL.
8. Se não existir, executa exatamente o DDL produzido pelo backend.
9. O resultado normalizado retorna ao controller e então ao frontend.

O adapter não aceitará SQL recebido diretamente do navegador.

## 8. Regras de segurança desta etapa

### Permitido

- DDL `CREATE TABLE` originado exclusivamente de `OracleScriptGenerator`;
- metadados consultados via query parametrizada;
- conexão com usuário de aplicação de privilégio limitado.

### Não permitido

- SQL livre;
- DDL fornecido pelo request;
- `DROP` automático;
- múltiplos comandos arbitrários em uma única execução;
- uso das credenciais `SYS` ou `SYSTEM` pelo backend;
- exposição de senha ou URL JDBC ao cliente.

A validação de identificadores já existente no domínio permanece como primeira barreira contra nomes malformados. A principal barreira de segurança, porém, é estrutural: o backend gera o SQL a partir de objetos validados em vez de executar texto enviado pelo usuário.

## 9. JDBC e configuração

O backend deverá usar o Oracle JDBC Thin Driver compatível com Java 21.

As credenciais serão lidas de variáveis de ambiente, com propriedades Spring correspondentes.

### Backend executado no Windows

```text
ORACLE_JDBC_URL=jdbc:oracle:thin:@//localhost:51521/FREEPDB1
ORACLE_USERNAME=dbeduca
ORACLE_PASSWORD=dbeduca123
```

### Backend executado no Docker Compose

```text
ORACLE_JDBC_URL=jdbc:oracle:thin:@//lab-oracle:1521/FREEPDB1
ORACLE_USERNAME=dbeduca
ORACLE_PASSWORD=dbeduca123
```

A diferença entre os ambientes ficará somente na configuração. Nenhuma classe Java deverá conter host, porta, usuário ou senha fixos.

## 10. Comportamento do Oracle

A verificação de existência será feita no schema do usuário de aplicação:

```sql
SELECT COUNT(*)
FROM USER_TABLES
WHERE TABLE_NAME = ?
```

O nome será normalizado para maiúsculas antes da consulta, mantendo o comportamento padrão de identificadores Oracle não delimitados.

Se o objeto existir, nenhuma instrução DDL será executada.

O adapter utilizará uma conexão JDBC aberta somente pelo tempo necessário para a verificação e execução. O desenho desta etapa não exige JPA, Hibernate ou entidades persistentes.

## 11. Tratamento de erros

Mapeamento previsto:

| Situação | HTTP | Comportamento |
|---|---:|---|
| Estrutura inválida | 400 | mensagem de validação já usada pela API |
| Engine ainda não executável | 400 | mensagem informando disponibilidade atual |
| Tabela já existente | 409 | mensagem pedagógica, sem `DROP` |
| Oracle indisponível/conexão recusada | 503 | mensagem genérica de ambiente indisponível |
| Erro inesperado | 500 | mensagem genérica ao cliente; detalhe técnico apenas no backend |
| Sucesso | 200 | resultado normalizado da execução |

Erros Oracle específicos não devem vazar credenciais, URL de conexão ou stack trace para a resposta HTTP.

## 12. Frontend

A interface atual será preservada.

Quando `engine === 'ORACLE'` e existir um script gerado:

```text
[ Gerar script ]
       ↓
janela de código
       ↓
[ Executar no Oracle ]
       ↓
resultado
```

### Estados do botão

- sem script: oculto ou desabilitado;
- script Oracle válido: habilitado;
- requisição em andamento: `Executando…` e desabilitado;
- sucesso: permanece disponível, mas uma nova tentativa contra a mesma tabela resultará em `409`;
- outras engines: não mostrar botão de execução neste incremento.

### Feedback

Sucesso:

```text
Tabela ALUNOS criada com sucesso no Oracle.
```

Conflito:

```text
A tabela ALUNOS já existe no Oracle.
```

Indisponibilidade:

```text
Oracle temporariamente indisponível. Verifique o ambiente do laboratório.
```

A caixa “Modo seguro do MVP” será atualizada para explicar que a execução Oracle é controlada pelo backend e que SQL livre continua desabilitado.

O tema Light/Dark e a estrutura visual existente serão mantidos.

## 13. Testes

A implementação seguirá TDD.

### Testes de serviço/adapters

Cobrir pelo menos:

- geração do DDL Oracle antes da execução;
- execução do DDL quando a tabela não existe;
- não execução quando a tabela já existe;
- transformação de falha de conexão em `DatabaseUnavailableException`;
- rejeição de engine não habilitada para execução.

### Testes MVC

Cobrir pelo menos:

- `POST /api/v1/executions` com Oracle válido retorna `200`;
- tabela existente retorna `409`;
- engine não suportada para execução retorna `400`;
- indisponibilidade retorna `503`;
- resposta não contém senha, URL JDBC ou credenciais.

### Regressão

Todos os testes existentes de geração de scripts e listagem de engines deverão continuar passando.

### Validação real

Após a suíte automatizada:

1. iniciar `lab-oracle`;
2. confirmar estado `healthy`;
3. iniciar backend com as variáveis JDBC locais;
4. gerar uma tabela Oracle pelo frontend;
5. executar pelo botão;
6. confirmar a tabela em `USER_TABLES` ou via SQL*Plus;
7. executar novamente e confirmar resposta `409` sem alteração destrutiva.

## 14. Critérios de aceite

O incremento é considerado concluído quando todos os itens abaixo forem comprovados:

- Oracle aparece como engine suportada;
- a geração de DDL Oracle continua funcionando;
- o backend conecta ao Oracle com usuário de aplicação;
- o frontend consegue solicitar execução de uma estrutura Oracle;
- o SQL executado é regenerado pelo backend;
- uma tabela inexistente é criada com sucesso;
- uma tabela existente retorna `409`;
- nenhuma operação automática de `DROP` é realizada;
- credenciais não são expostas à interface ou resposta da API;
- outras engines não ganham execução real acidentalmente;
- testes automatizados existentes e novos passam;
- teste manual com o container Oracle confirma o fluxo ponta a ponta.

## 15. Evolução posterior

Depois deste incremento estabilizado, o próximo recorte do Sprint 2 poderá introduzir o editor SQL controlado. Esse recurso deverá ter um design separado para classificação de comandos, políticas de permissão, timeout, limites de resultado, isolamento e auditoria.

A mesma abstração `DatabaseExecutionAdapter` poderá então receber adapters adicionais para PostgreSQL, MySQL e MongoDB, preservando o controller e o contrato de alto nível sempre que as diferenças de engine permitirem.
