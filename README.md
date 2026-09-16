# DBEduca — Plataforma Educacional de Bancos de Dados

O **DBEduca** é um laboratório educacional para ensinar modelagem, SQL e NoSQL de forma prática. O aluno escolhe uma tecnologia, define a estrutura dos dados e o sistema gera o script correspondente com validações básicas de segurança.

## MVP 0.1

Nesta versão:

- Java 21 + Spring Boot 3.5.5 no backend;
- React 19 no frontend;
- PostgreSQL, MySQL e MongoDB como engines iniciais;
- modelagem simples de tabela/collection;
- campos dinâmicos;
- PRIMARY KEY, NOT NULL e UNIQUE;
- geração de DDL PostgreSQL/MySQL;
- geração de script MongoDB;
- validação de identificadores no backend;
- tema claro/escuro;
- Docker Compose com os três bancos;
- testes unitários do núcleo e testes MVC da API.

> Por segurança, o MVP gera os comandos mas **não executa SQL arbitrário enviado pelo navegador**. A execução controlada em sandbox é o objetivo do Sprint 2.

## Arquitetura

![Visão geral do DBEduca](docs/assets/dbeduca-architecture.png)

```text
React
  |
  v
Spring Boot REST API
  |
  v
ScriptGeneratorRegistry
  |---------------------------|
  v             v             v
PostgreSQL     MySQL        MongoDB
Adapter        Adapter       Adapter
```

A interface `ScriptGenerator` permite adicionar Oracle, SQL Server, SQLite, Redis e outros bancos sem acoplar o domínio à interface web.

## Estrutura

```text
DBEduca/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
├── docs/
│   └── superpowers/
├── docker-compose.yml
├── .env.example
└── README.md
```

## Subir tudo com Docker Desktop

Na raiz do projeto, você pode usar o atalho:

```powershell
.\start.ps1
```

Ou executar manualmente:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Depois acesse:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

Bancos do laboratório:

| Banco | Host | Porta externa | Banco | Usuário padrão |
|---|---|---:|---|---|
| PostgreSQL | localhost | 55432 | dbeduca_lab | dbeduca |
| MySQL | localhost | 53306 | dbeduca_lab | dbeduca |
| MongoDB | localhost | 57017 | admin | dbeduca |

As senhas de desenvolvimento estão em `.env.example` e devem ser alteradas antes de qualquer publicação.

## Executar sem Docker

### Backend

Requisitos: Java 21 e Maven 3.9+.

```powershell
cd backend
mvn spring-boot:run
```

### Frontend

Requisitos: Node.js 22+.

```powershell
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`.

## API

### Engines disponíveis

```http
GET /api/v1/platform/engines
```

### Gerar script

```http
POST /api/v1/scripts/generate
Content-Type: application/json
```

Exemplo:

```json
{
  "engine": "POSTGRESQL",
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

## Roadmap

### Sprint 2 — Laboratório de execução
- executor isolado por aluno/projeto;
- schemas PostgreSQL por projeto;
- databases MySQL isolados;
- databases/collections MongoDB isolados;
- editor SQL/NoSQL;
- timeout, limite de linhas e bloqueio de operações perigosas;
- histórico de execução.

### Sprint 3 — Plataforma educacional
- login;
- Spring Security + JWT;
- RBAC: ADMIN, PROFESSOR e ALUNO;
- turmas;
- atividades;
- correção automática;
- nota e histórico de tentativas.

### Sprint 4 — Tutor IA híbrido
- provider externo;
- provider local;
- níveis de dica 1 a 4;
- contexto da tentativa do aluno;
- modo prova configurável pelo professor;
- auditoria de uso da IA.

### Sprint 5 — Expansão de engines
- Oracle;
- SQL Server;
- SQLite;
- Redis;
- Cassandra.

## Testes

```powershell
cd backend
mvn test
```

O núcleo de geração também foi desenhado sem dependências de Spring para facilitar testes unitários rápidos.

## Segurança pedagógica

O objetivo é permitir experimentação sem dar ao navegador acesso direto às credenciais dos bancos. Nas próximas versões, toda execução deverá passar por políticas de isolamento, timeout, quotas e auditoria no backend.
