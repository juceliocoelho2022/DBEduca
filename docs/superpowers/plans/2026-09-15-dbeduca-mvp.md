# DBEduca MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar um MVP executável do DBEduca que gere scripts educacionais para PostgreSQL, MySQL e MongoDB a partir de uma estrutura informada no frontend.

**Architecture:** O frontend React envia um modelo de tabela para uma API Spring Boot. Um registry desacoplado seleciona o adapter da engine, e cada adapter gera seu script sem executar comandos arbitrários.

**Tech Stack:** Java 21, Spring Boot 3.5.5, React 19, Vite 7, Docker Compose, PostgreSQL 17, MySQL 8.4 e MongoDB 8.

**Spec:** `docs/superpowers/specs/2026-09-15-dbeduca-mvp-design.md`

## Global Constraints
- Java 21.
- Três engines iniciais: PostgreSQL, MySQL e MongoDB.
- Nenhuma execução de SQL arbitrário nesta versão.
- Identificadores devem ser validados antes da geração.

---

### Task 1: Núcleo de geração multi-banco
**Files:** `backend/src/main/java/com/dbeduca/core/*`, `backend/src/test/java/com/dbeduca/core/*`
**Interfaces:** `ScriptGenerator.generate(TableDefinition)` produz `String`; `ScriptGeneratorRegistry.generate(DatabaseEngine, TableDefinition)` seleciona a implementação.
- [x] Escrever teste que referencia os geradores antes da implementação.
- [x] Executar o teste e confirmar falha por classes ausentes.
- [x] Implementar domínio, validação e três geradores.
- [x] Reexecutar o smoke test e confirmar sucesso.

### Task 2: API REST
**Files:** `backend/src/main/java/com/dbeduca/api/*`, `backend/src/test/java/com/dbeduca/api/*`
**Interfaces:** `GET /api/v1/platform/engines`; `POST /api/v1/scripts/generate`.
- [x] Criar testes MVC para catálogo e geração.
- [x] Implementar aplicação, serviços e controllers.
- [x] Verificar estrutura e configuração Spring por inspeção/configuração; build Maven completo depende de download externo.

### Task 3: Frontend React
**Files:** `frontend/src/*`, `frontend/package.json`, `frontend/vite.config.js`
**Interfaces:** Consome `/api/v1/platform/engines` e `/api/v1/scripts/generate`.
- [x] Criar modelador simples com seleção de engine e campos dinâmicos.
- [x] Criar painel de script gerado e mensagens de erro.
- [x] Criar layout responsivo light/dark-ready.

### Task 4: Ambiente e documentação
**Files:** `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `README.md`, `.env.example`.
- [x] Adicionar containers dos três bancos e serviços da aplicação.
- [x] Documentar execução local, endpoints e roadmap.
- [x] Executar verificações locais disponíveis; empacotamento realizado ao final da sessão.
