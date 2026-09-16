# DBEduca MVP — Design

## Objetivo
Criar a primeira versão executável de uma plataforma educacional multi-banco em que o aluno seleciona PostgreSQL, MySQL ou MongoDB, define uma estrutura de dados e recebe o script correspondente.

## Escopo desta versão
- Frontend React com modelador simples por formulário.
- Backend Java 21 + Spring Boot com API REST.
- Geração de DDL para PostgreSQL e MySQL.
- Geração de script de criação de collection para MongoDB.
- Catálogo de engines e tipos suportados.
- Docker Compose com PostgreSQL, MySQL e MongoDB prontos para as próximas etapas.
- Testes do núcleo de geração.

## Fora do escopo deste incremento
A execução de SQL arbitrário, autenticação JWT, RBAC, turmas, correção automática e tutor de IA serão adicionados em incrementos posteriores. Essa separação reduz risco ao executar comandos de alunos e mantém o primeiro incremento testável.

## Arquitetura
React chama a API REST Spring Boot. A API encaminha a solicitação para um `ScriptGeneratorRegistry`, que seleciona um adapter de banco (`PostgresScriptGenerator`, `MySqlScriptGenerator` ou `MongoScriptGenerator`). O domínio de modelagem é independente do Spring para permitir testes rápidos e novos adapters.

## Segurança
Nomes de tabelas e colunas aceitam apenas identificadores simples `[A-Za-z_][A-Za-z0-9_]*`. Tipos SQL são validados contra padrões restritos. Neste MVP nenhum script fornecido pelo usuário é executado pelo backend.
