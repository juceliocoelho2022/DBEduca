<div align="center">

# DBEduca

### Plataforma educacional para aprender Banco de Dados na prática

Modelagem • SQL • NoSQL • geração de scripts • laboratório multi-banco

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19.3-61DAFB?logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-8.2.2-646CFF?logo=vite&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-8.0-47A248?logo=mongodb&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle-Free%2023-F80000?logo=oracle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

</div>

---

## Sobre o projeto

O **DBEduca** é uma plataforma educacional criada para aproximar o ensino de Banco de Dados da prática profissional.

O aluno escolhe uma engine, define a estrutura dos dados e visualiza o script correspondente. A proposta é permitir que conceitos como tabelas, collections, tipos de dados, chaves, restrições e diferenças entre bancos relacionais e NoSQL sejam estudados de forma prática.

O projeto evolui de um gerador didático de scripts para um **laboratório educacional multi-engine**, com execução controlada, editor SQL/NoSQL, atividades, correção automática, acompanhamento do professor e tutor de IA.

> **Status atual:** MVP 0.1 evoluindo para o Sprint 2. O sistema já gera scripts para PostgreSQL, MySQL, MongoDB e Oracle e possui containers locais para as quatro engines. A execução arbitrária de comandos enviados pelo navegador ainda não está habilitada.

---

## Objetivos

- tornar o estudo de Banco de Dados mais visual e prático;
- permitir comparação entre diferentes engines;
- gerar SQL/NoSQL a partir da estrutura definida pelo aluno;
- mostrar diferenças reais de tipos e sintaxe entre bancos;
- oferecer uma arquitetura extensível para novas engines;
- preparar um ambiente seguro para execução de exercícios;
- evoluir para perfis de Administrador, Professor e Aluno.

---

## Engines suportadas

| Engine | Modelo | Geração de script | Container local | Execução controlada |
|---|---|---:|---:|---:|
| PostgreSQL 17 | Relacional | ✅ | ✅ | Sprint 2 |
| MySQL 8.4 | Relacional | ✅ | ✅ | Sprint 2 |
| MongoDB 8.0 | Documental | ✅ | ✅ | Sprint 2 |
| Oracle Database Free 23 | Relacional | ✅ | ✅ | Sprint 2 |

---

## Funcionalidades atuais

- seleção entre PostgreSQL, MySQL, MongoDB e Oracle;
- definição de tabela ou collection;
- criação dinâmica de campos;
- configuração de `PRIMARY KEY`;
- configuração de `NOT NULL`;
- configuração de `UNIQUE`;
- geração de DDL PostgreSQL;
- geração de DDL MySQL;
- geração de script MongoDB;
- geração de DDL Oracle;
- adaptação de tipos comuns para Oracle;
- validação de identificadores no backend;
- interface React com tema claro/escuro;
- API REST com Spring Boot;
- Docker Compose com quatro bancos;
- testes unitários do núcleo e testes MVC da API.

---

## Arquitetura

```text
┌──────────────────────────────┐
│          React 19            │
│      Interface do aluno      │
└──────────────┬───────────────┘
               │ HTTP/JSON
               ▼
┌──────────────────────────────┐
│ Java 21 + Spring Boot 3.5.5  │
│          REST API            │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│   ScriptGeneratorRegistry    │
└──────┬───────┬───────┬──────┘
       │       │       │
       ▼       ▼       ▼       ▼
 PostgreSQL  MySQL   MongoDB  Oracle
  Adapter    Adapter  Adapter  Adapter
```

O contrato `ScriptGenerator` funciona como ponto de extensão para as engines. O `ScriptGeneratorRegistry` resolve o adapter correspondente sem acoplar a camada web às implementações específicas.

### Padrões e princípios usados

- Adapter / Strategy para engines;
- Registry Pattern para resolução de geradores;
- API REST;
- separação frontend/backend;
- validação no backend;
- isolamento de infraestrutura via Docker;
- evolução incremental orientada a testes.

---

# Stack tecnológica

## Backend

- **Java 21**;
- **Spring Boot 3.5.5**;
- Spring Web;
- Bean Validation / Jakarta Validation;
- Spring Boot Actuator;
- Maven;
- JUnit 5;
- MockMvc / Spring Boot Test.

## Frontend

- **React 19.3.0**;
- **Vite 8.2.2**;
- JavaScript ES Modules;
- CSS;
- tema Light/Dark;
- Nginx 1.27 no container de produção.

## Bancos de dados

- **PostgreSQL 17**;
- **MySQL 8.4**;
- **MongoDB 8.0**;
- **Oracle Database Free 23**.

## Infraestrutura e DevOps

- Docker;
- Docker Compose;
- Node.js 22 no estágio de build do frontend;
- Maven 3.9.11 no estágio de build do backend;
- Eclipse Temurin 21;
- Git;
- GitHub;
- Nginx.

---

## Oracle no DBEduca

O adapter Oracle converte tipos comuns do modelador para equivalentes adequados ao Oracle.

Exemplos:

| Tipo informado | Oracle |
|---|---|
| `BIGINT` | `NUMBER(19)` |
| `INTEGER` | `NUMBER(10)` |
| `VARCHAR(100)` | `VARCHAR2(100)` |
| `TEXT` | `CLOB` |
| `DECIMAL(10,2)` | `NUMBER(10,2)` |
| `DATE` | `DATE` |
| `TIMESTAMP` | `TIMESTAMP` |

Exemplo:

```sql
CREATE TABLE alunos (
    id NUMBER(19) PRIMARY KEY NOT NULL,
    nome VARCHAR2(100) NOT NULL,
    email VARCHAR2(150) UNIQUE
);
```

---

## Estrutura do repositório

```text
DBEduca/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│
├── docs/
│   ├── assets/
│   └── superpowers/
│
├── docker-compose.yml
├── .env.example
├── start.ps1
├── stop.ps1
└── README.md
```

---

## Como executar com Docker

Na raiz do projeto:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Ou:

```powershell
.\start.ps1
```

### Serviços

| Serviço | Endereço |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |

### Bancos do laboratório

| Banco | Host | Porta externa | Database/Service | Usuário padrão |
|---|---|---:|---|---|
| PostgreSQL | `localhost` | `55432` | `dbeduca_lab` | `dbeduca` |
| MySQL | `localhost` | `53306` | `dbeduca_lab` | `dbeduca` |
| MongoDB | `localhost` | `57017` | `admin` | `dbeduca` |
| Oracle Free | `localhost` | `51521` | `FREEPDB1` | `dbeduca` |

Para Oracle, a conexão local segue o formato:

```text
Host: localhost
Port: 51521
Service: FREEPDB1
User: dbeduca
```

> As credenciais de `.env.example` são apenas para desenvolvimento local. Troque-as antes de qualquer publicação.

---

## Executar manualmente

### Backend

Requisitos:

- JDK 21 ou superior com suporte a `--release 21`;
- Maven 3.9+.

```powershell
cd backend
mvn clean spring-boot:run
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

### Frontend

Requisito: Node.js 22+.

```powershell
cd frontend
npm install
npm run dev
```

Acesse:

```text
http://localhost:5173
```

---

## API REST

### Listar engines

```http
GET /api/v1/platform/engines
```

Resposta inclui:

```text
POSTGRESQL
MYSQL
MONGODB
ORACLE
```

### Gerar script

```http
POST /api/v1/scripts/generate
Content-Type: application/json
```

Exemplo Oracle:

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

---

## Testes

```powershell
cd backend
mvn test
```

O núcleo de geração foi mantido com baixo acoplamento ao Spring para permitir testes rápidos dos adapters.

---

## Segurança pedagógica

O navegador não deve receber credenciais nem acessar diretamente as instâncias dos bancos.

A execução do Sprint 2 seguirá princípios como:

- isolamento por aluno/projeto;
- timeout de execução;
- limite de linhas retornadas;
- bloqueio de operações perigosas;
- quotas;
- auditoria;
- histórico de comandos;
- validação no backend.

---

## Roadmap

### Sprint 2 — Laboratório de execução

- [x] Oracle como quarta engine de geração;
- [x] Oracle Free no Docker Compose;
- [ ] execução controlada no PostgreSQL;
- [ ] execução controlada no MySQL;
- [ ] execução controlada no MongoDB;
- [ ] execução controlada no Oracle;
- [ ] isolamento por projeto/aluno;
- [ ] editor SQL/NoSQL;
- [ ] timeout e limite de resultados;
- [ ] histórico de execução.

### Sprint 3 — Plataforma educacional

- [ ] autenticação;
- [ ] Spring Security + JWT;
- [ ] RBAC com `ADMIN`, `PROFESSOR` e `ALUNO`;
- [ ] turmas;
- [ ] atividades;
- [ ] correção automática;
- [ ] notas;
- [ ] histórico de tentativas.

### Sprint 4 — Tutor de IA híbrido

- [ ] provider de IA externo;
- [ ] provider local;
- [ ] níveis de ajuda de 1 a 4;
- [ ] análise da tentativa do aluno;
- [ ] modo prova configurável;
- [ ] auditoria do uso da IA.

### Sprint 5 — Expansão de engines

- [ ] SQL Server;
- [ ] SQLite;
- [ ] Redis;
- [ ] Cassandra.

---

## Público-alvo

- estudantes de Desenvolvimento de Sistemas;
- cursos técnicos;
- professores de Banco de Dados;
- escolas e instituições de ensino;
- estudantes iniciantes em SQL e NoSQL;
- pessoas que desejam comparar diferentes tecnologias de persistência.

---

## Autor

**Jucelio Farias Coelho**

Projeto desenvolvido com foco em educação tecnológica, Banco de Dados e construção de uma plataforma extensível para ensino de SQL e NoSQL.
