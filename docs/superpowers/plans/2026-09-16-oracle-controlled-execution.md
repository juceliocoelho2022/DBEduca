# Oracle Controlled Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que o DBEduca execute, via JDBC e de forma controlada, o `CREATE TABLE` Oracle gerado pelo próprio backend a partir do modelo validado pelo aluno, sem aceitar SQL livre e sem apagar tabelas existentes.

**Architecture:** O `ExecutionController` recebe a mesma estrutura de tabela usada na geração de scripts e delega ao `DatabaseExecutionService`. O serviço regenera o DDL usando o `ScriptGeneratorRegistry` e seleciona um `DatabaseExecutionAdapter`; nesta etapa somente `OracleExecutionAdapter` estará registrado. O adapter usa um `DataSource` Oracle para consultar `USER_TABLES` com bind parameter e, se a tabela não existir, executa exatamente o DDL regenerado pelo backend.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Web MVC, Jakarta Validation, Oracle JDBC Thin (`ojdbc11`), JDBC (`java.sql`/`javax.sql.DataSource`), JUnit 5, Mockito, MockMvc, React 19.3, Vite 8.2.2, Docker Compose, Oracle Database Free.

**Spec:** `docs/superpowers/specs/2026-09-16-oracle-controlled-execution-design.md`

## Global Constraints

- A primeira engine com execução real é `ORACLE`.
- O navegador nunca envia SQL arbitrário para execução.
- O backend regenera o DDL a partir de `TableDefinition` usando `ScriptGeneratorRegistry`.
- Somente `CREATE TABLE` gerado pelo `OracleScriptGenerator` pode chegar ao adapter nesta etapa.
- Tabela existente retorna `409 Conflict`; não executar `DROP TABLE`, recriação automática ou qualquer operação destrutiva.
- PostgreSQL, MySQL e MongoDB permanecem apenas com geração de script neste incremento.
- Credenciais Oracle ficam somente em variáveis de ambiente/configuração do backend.
- O backend usa o usuário de aplicação `dbeduca`, nunca `SYS` ou `SYSTEM`.
- A aplicação deve conseguir iniciar mesmo com o Oracle indisponível; a conexão é aberta apenas quando houver uma solicitação de execução.
- O SQL revisado na tela deve corresponder ao modelo executado: qualquer alteração de engine, tabela ou colunas invalida o script previamente gerado e o resultado de execução.
- Manter tema Light/Dark e estrutura visual atual.
- TDD obrigatório para código Java: teste falhando, implementação mínima, teste passando, refatoração.

---

## File Map

### Novos arquivos de produção

- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionAdapter.java` — contrato comum para adapters de execução.
- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionService.java` — regenera o DDL, seleciona adapter por engine e coordena a execução.
- `backend/src/main/java/com/dbeduca/execution/ExecutionResult.java` — resultado normalizado retornado pela camada de execução.
- `backend/src/main/java/com/dbeduca/execution/TableAlreadyExistsException.java` — conflito de objeto existente.
- `backend/src/main/java/com/dbeduca/execution/DatabaseUnavailableException.java` — falha ao obter conexão com a engine.
- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionException.java` — falha JDBC inesperada durante metadados/DDL.
- `backend/src/main/java/com/dbeduca/execution/OracleExecutionAdapter.java` — verificação em `USER_TABLES` e execução do DDL Oracle.
- `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseRequest.java` — request validado que converte para `TableDefinition`.
- `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseResponse.java` — resposta REST de sucesso.
- `backend/src/main/java/com/dbeduca/api/ExecutionController.java` — `POST /api/v1/executions`.

### Novos testes

- `backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java` — seleção de adapter, regeneração do DDL e rejeição de engine não habilitada.
- `backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java` — criação, conflito e indisponibilidade JDBC.
- `backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java` — contratos HTTP 200/400/409/503/500 e ausência de credenciais.

### Arquivos existentes a modificar

- `backend/pom.xml` — adicionar `ojdbc11`.
- `backend/src/main/java/com/dbeduca/config/ApplicationConfig.java` — criar `oracleDataSource`, `OracleExecutionAdapter` e `DatabaseExecutionService` sem abrir conexão no startup.
- `backend/src/main/java/com/dbeduca/api/ApiExceptionHandler.java` — mapear exceções da execução.
- `backend/src/main/resources/application.properties` — propriedades Oracle derivadas de variáveis de ambiente.
- `.env.example` — documentar `ORACLE_JDBC_URL`, `ORACLE_USERNAME`, `ORACLE_PASSWORD`.
- `docker-compose.yml` — injetar URL interna e credenciais no backend.
- `frontend/src/api.js` — adicionar `executeDatabase(payload)`.
- `frontend/src/App.jsx` — estado de execução, invalidação de script e botão Oracle.
- `frontend/src/styles.css` — feedback visual de sucesso/conflito/erro.
- `README.md` — documentar endpoint, fluxo e configuração Oracle.

---

### Task 1: Criar o contrato de execução e o serviço coordenador

**Files:**
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionAdapter.java`
- Create: `backend/src/main/java/com/dbeduca/execution/ExecutionResult.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionService.java`
- Test: `backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java`

**Interfaces:**
- Consumes: `DatabaseEngine`, `TableDefinition`, `ScriptGeneratorRegistry`.
- Produces: `DatabaseExecutionAdapter.engine()`, `DatabaseExecutionAdapter.executeCreateTable(String tableName, String ddl)`, `DatabaseExecutionService.execute(DatabaseEngine engine, TableDefinition table)`, `ExecutionResult`.

- [ ] **Step 1: Write the failing service tests**

Create `DatabaseExecutionServiceTest.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseExecutionServiceTest {
    private final TableDefinition table = new TableDefinition("alunos", List.of(
        new ColumnDefinition("id", "BIGINT", true, true, false),
        new ColumnDefinition("nome", "VARCHAR(100)", false, true, false)
    ));

    @Test
    void regeneratesOracleDdlAndDelegatesToOracleAdapter() {
        var registry = new ScriptGeneratorRegistry(List.of(new OracleScriptGenerator()));
        var adapter = new CapturingAdapter();
        var service = new DatabaseExecutionService(registry, List.of(adapter));

        var result = service.execute(DatabaseEngine.ORACLE, table);

        assertEquals(DatabaseEngine.ORACLE, result.engine());
        assertEquals("SUCCESS", result.status());
        assertEquals("TABLE", result.objectType());
        assertEquals("ALUNOS", result.objectName());
        assertTrue(adapter.ddl.contains("CREATE TABLE alunos"));
        assertTrue(adapter.ddl.contains("id NUMBER(19) PRIMARY KEY NOT NULL"));
        assertEquals(adapter.ddl, result.script());
    }

    @Test
    void rejectsEngineWithoutExecutionAdapter() {
        var registry = new ScriptGeneratorRegistry(List.of(new PostgresScriptGenerator()));
        var service = new DatabaseExecutionService(registry, List.of());

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.execute(DatabaseEngine.POSTGRESQL, table));

        assertEquals("Execução ainda não habilitada para POSTGRESQL.", error.getMessage());
    }

    private static final class CapturingAdapter implements DatabaseExecutionAdapter {
        private String ddl;

        @Override
        public DatabaseEngine engine() {
            return DatabaseEngine.ORACLE;
        }

        @Override
        public ExecutionResult executeCreateTable(String tableName, String ddl) {
            this.ddl = ddl;
            return new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                tableName.toUpperCase(),
                "Tabela " + tableName.toUpperCase() + " criada com sucesso no Oracle.",
                ddl
            );
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `backend/`:

```powershell
mvn -Dtest=DatabaseExecutionServiceTest test
```

Expected: compilation/test failure because `com.dbeduca.execution` types do not exist yet.

- [ ] **Step 3: Implement the minimal execution contract and result**

Create `DatabaseExecutionAdapter.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public interface DatabaseExecutionAdapter {
    DatabaseEngine engine();
    ExecutionResult executeCreateTable(String tableName, String ddl);
}
```

Create `ExecutionResult.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public record ExecutionResult(
    DatabaseEngine engine,
    String status,
    String objectType,
    String objectName,
    String message,
    String script
) {}
```

Create `DatabaseExecutionService.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.core.ScriptGeneratorRegistry;
import com.dbeduca.core.TableDefinition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DatabaseExecutionService {
    private final ScriptGeneratorRegistry scriptRegistry;
    private final Map<DatabaseEngine, DatabaseExecutionAdapter> adapters = new EnumMap<>(DatabaseEngine.class);

    public DatabaseExecutionService(
        ScriptGeneratorRegistry scriptRegistry,
        List<DatabaseExecutionAdapter> adapters
    ) {
        this.scriptRegistry = scriptRegistry;
        adapters.forEach(adapter -> this.adapters.put(adapter.engine(), adapter));
    }

    public ExecutionResult execute(DatabaseEngine engine, TableDefinition table) {
        var adapter = adapters.get(engine);
        if (adapter == null) {
            throw new IllegalArgumentException("Execução ainda não habilitada para " + engine + ".");
        }

        String ddl = scriptRegistry.generate(engine, table);
        return adapter.executeCreateTable(table.name(), ddl);
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

```powershell
mvn -Dtest=DatabaseExecutionServiceTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Run the existing core regression tests**

```powershell
mvn -Dtest=ScriptGeneratorTest,DatabaseExecutionServiceTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add backend/src/main/java/com/dbeduca/execution backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java
git commit -m "feat: add database execution service contract"
```

---

### Task 2: Implementar o OracleExecutionAdapter com JDBC e proteção contra tabela existente

**Files:**
- Create: `backend/src/main/java/com/dbeduca/execution/TableAlreadyExistsException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseUnavailableException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/OracleExecutionAdapter.java`
- Test: `backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java`

**Interfaces:**
- Consumes: `javax.sql.DataSource`, `DatabaseExecutionAdapter`.
- Produces: `OracleExecutionAdapter(DataSource)`, `TableAlreadyExistsException.engine()`, `TableAlreadyExistsException.objectName()`, `DatabaseUnavailableException.engine()`.

- [ ] **Step 1: Write failing adapter tests for success, conflict and unavailable database**

Create `OracleExecutionAdapterTest.java`:

```java
package com.dbeduca.execution;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OracleExecutionAdapterTest {
    private static final String DDL = "CREATE TABLE alunos (id NUMBER(19) PRIMARY KEY NOT NULL);";

    @Test
    void createsTableWhenItDoesNotExist() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement ddlStatement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(0);
        when(connection.createStatement()).thenReturn(ddlStatement);

        var adapter = new OracleExecutionAdapter(dataSource);
        var result = adapter.executeCreateTable("alunos", DDL);

        verify(existsStatement).setString(1, "ALUNOS");
        verify(ddlStatement).executeUpdate(DDL);
        assertEquals("SUCCESS", result.status());
        assertEquals("ALUNOS", result.objectName());
        assertEquals(DDL, result.script());
    }

    @Test
    void refusesToReplaceExistingTable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        var adapter = new OracleExecutionAdapter(dataSource);

        var error = assertThrows(TableAlreadyExistsException.class,
            () -> adapter.executeCreateTable("alunos", DDL));

        assertEquals("ALUNOS", error.objectName());
        verify(connection, never()).createStatement();
    }

    @Test
    void mapsConnectionFailureToDatabaseUnavailable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        var adapter = new OracleExecutionAdapter(dataSource);

        var error = assertThrows(DatabaseUnavailableException.class,
            () -> adapter.executeCreateTable("alunos", DDL));

        assertEquals(com.dbeduca.core.DatabaseEngine.ORACLE, error.engine());
    }
}
```

- [ ] **Step 2: Run the adapter test and verify RED**

```powershell
mvn -Dtest=OracleExecutionAdapterTest test
```

Expected: compilation failure because Oracle execution classes do not exist.

- [ ] **Step 3: Implement the three domain exceptions**

Create `TableAlreadyExistsException.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class TableAlreadyExistsException extends RuntimeException {
    private final DatabaseEngine engine;
    private final String objectName;

    public TableAlreadyExistsException(DatabaseEngine engine, String objectName) {
        super("A tabela " + objectName + " já existe no Oracle.");
        this.engine = engine;
        this.objectName = objectName;
    }

    public DatabaseEngine engine() { return engine; }
    public String objectName() { return objectName; }
}
```

Create `DatabaseUnavailableException.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class DatabaseUnavailableException extends RuntimeException {
    private final DatabaseEngine engine;

    public DatabaseUnavailableException(DatabaseEngine engine, Throwable cause) {
        super("Oracle temporariamente indisponível. Verifique o ambiente do laboratório.", cause);
        this.engine = engine;
    }

    public DatabaseEngine engine() { return engine; }
}
```

Create `DatabaseExecutionException.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class DatabaseExecutionException extends RuntimeException {
    private final DatabaseEngine engine;

    public DatabaseExecutionException(DatabaseEngine engine, Throwable cause) {
        super("Não foi possível executar a operação no Oracle.", cause);
        this.engine = engine;
    }

    public DatabaseEngine engine() { return engine; }
}
```

- [ ] **Step 4: Implement the minimal Oracle adapter**

Create `OracleExecutionAdapter.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public final class OracleExecutionAdapter implements DatabaseExecutionAdapter {
    private static final String TABLE_EXISTS_SQL = """
        SELECT COUNT(*)
        FROM USER_TABLES
        WHERE TABLE_NAME = ?
        """;

    private final DataSource dataSource;

    public OracleExecutionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.ORACLE;
    }

    @Override
    public ExecutionResult executeCreateTable(String tableName, String ddl) {
        String normalizedName = tableName.toUpperCase(Locale.ROOT);

        try (Connection connection = openConnection()) {
            if (tableExists(connection, normalizedName)) {
                throw new TableAlreadyExistsException(DatabaseEngine.ORACLE, normalizedName);
            }

            try (var statement = connection.createStatement()) {
                statement.executeUpdate(ddl);
            }

            return new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                normalizedName,
                "Tabela " + normalizedName + " criada com sucesso no Oracle.",
                ddl
            );
        } catch (TableAlreadyExistsException | DatabaseUnavailableException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseExecutionException(DatabaseEngine.ORACLE, ex);
        }
    }

    private Connection openConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new DatabaseUnavailableException(DatabaseEngine.ORACLE, ex);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (var statement = connection.prepareStatement(TABLE_EXISTS_SQL)) {
            statement.setString(1, tableName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
```

- [ ] **Step 5: Run adapter tests and verify GREEN**

```powershell
mvn -Dtest=OracleExecutionAdapterTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run service + adapter regression**

```powershell
mvn -Dtest=DatabaseExecutionServiceTest,OracleExecutionAdapterTest,ScriptGeneratorTest test
```

Expected: all tests pass.

- [ ] **Step 7: Commit Task 2**

```powershell
git add backend/src/main/java/com/dbeduca/execution backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java
git commit -m "feat: execute generated Oracle DDL safely"
```

---

### Task 3: Configurar Oracle JDBC sem conectar no startup

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/dbeduca/config/ApplicationConfig.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `.env.example`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: `ORACLE_JDBC_URL`, `ORACLE_USERNAME`, `ORACLE_PASSWORD`.
- Produces: bean `oracleDataSource`, `OracleExecutionAdapter`, `DatabaseExecutionService`.

- [ ] **Step 1: Add the Oracle JDBC driver dependency**

Inside `<dependencies>` in `backend/pom.xml`, add the driver without an explicit version so Spring Boot dependency management controls the compatible version:

```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
</dependency>
```

Do not add JPA or Hibernate.

- [ ] **Step 2: Compile to verify the dependency resolves**

```powershell
mvn -DskipTests compile
```

Expected: `BUILD SUCCESS` and Oracle JDBC classes available on the classpath.

- [ ] **Step 3: Configure properties from environment variables**

Append to `backend/src/main/resources/application.properties`:

```properties
dbeduca.oracle.jdbc-url=${ORACLE_JDBC_URL:jdbc:oracle:thin:@//localhost:51521/FREEPDB1}
dbeduca.oracle.username=${ORACLE_USERNAME:dbeduca}
dbeduca.oracle.password=${ORACLE_PASSWORD:}
```

The password has no repository default. Local development must set `ORACLE_PASSWORD` explicitly.

- [ ] **Step 4: Wire DataSource, adapter and service in ApplicationConfig**

Extend `ApplicationConfig.java` with imports:

```java
import com.dbeduca.execution.DatabaseExecutionAdapter;
import com.dbeduca.execution.DatabaseExecutionService;
import com.dbeduca.execution.OracleExecutionAdapter;
import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.SQLException;
```

Keep the existing `scriptGeneratorRegistry()` bean and add:

```java
@Bean
DataSource oracleDataSource(
    @Value("${dbeduca.oracle.jdbc-url}") String jdbcUrl,
    @Value("${dbeduca.oracle.username}") String username,
    @Value("${dbeduca.oracle.password}") String password
) throws SQLException {
    var dataSource = new OracleDataSource();
    dataSource.setURL(jdbcUrl);
    dataSource.setUser(username);
    dataSource.setPassword(password);
    return dataSource;
}

@Bean
OracleExecutionAdapter oracleExecutionAdapter(DataSource oracleDataSource) {
    return new OracleExecutionAdapter(oracleDataSource);
}

@Bean
DatabaseExecutionService databaseExecutionService(
    ScriptGeneratorRegistry scriptGeneratorRegistry,
    OracleExecutionAdapter oracleExecutionAdapter
) {
    return new DatabaseExecutionService(
        scriptGeneratorRegistry,
        List.<DatabaseExecutionAdapter>of(oracleExecutionAdapter)
    );
}
```

Creating `OracleDataSource` configures connection metadata only; `getConnection()` remains inside `OracleExecutionAdapter`, so Oracle downtime does not prevent Spring Boot startup.

- [ ] **Step 5: Update `.env.example` with host-side settings**

Ensure these entries exist:

```dotenv
ORACLE_PASSWORD=oracle123
ORACLE_APP_USER=dbeduca
ORACLE_APP_USER_PASSWORD=dbeduca123
ORACLE_JDBC_URL=jdbc:oracle:thin:@//localhost:51521/FREEPDB1
ORACLE_USERNAME=dbeduca
```

For manual Windows execution, set backend `ORACLE_PASSWORD` to the application-user password used by JDBC:

```powershell
$env:ORACLE_JDBC_URL="jdbc:oracle:thin:@//localhost:51521/FREEPDB1"
$env:ORACLE_USERNAME="dbeduca"
$env:ORACLE_PASSWORD="dbeduca123"
```

- [ ] **Step 6: Inject internal Docker networking values into the backend service**

Under `backend:` in `docker-compose.yml`, add:

```yaml
environment:
  ORACLE_JDBC_URL: jdbc:oracle:thin:@//lab-oracle:1521/FREEPDB1
  ORACLE_USERNAME: ${ORACLE_APP_USER:-dbeduca}
  ORACLE_PASSWORD: ${ORACLE_APP_USER_PASSWORD:-dbeduca123}
```

Do not add a hard `depends_on: condition: service_healthy`; the backend must still start even when Oracle is unavailable, and the execution endpoint is responsible for returning `503` on connection failure.

- [ ] **Step 7: Run the full backend test suite**

```powershell
mvn clean test
```

Expected: all existing and new tests pass with `Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit Task 3**

```powershell
git add backend/pom.xml backend/src/main/java/com/dbeduca/config/ApplicationConfig.java backend/src/main/resources/application.properties .env.example docker-compose.yml
git commit -m "chore: configure Oracle JDBC execution"
```

---

### Task 4: Expor POST /api/v1/executions e mapear erros HTTP

**Files:**
- Create: `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseRequest.java`
- Create: `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseResponse.java`
- Create: `backend/src/main/java/com/dbeduca/api/ExecutionController.java`
- Modify: `backend/src/main/java/com/dbeduca/api/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java`

**Interfaces:**
- Consumes: `DatabaseExecutionService.execute(DatabaseEngine, TableDefinition)`.
- Produces: `POST /api/v1/executions`; success JSON fields `engine`, `status`, `objectType`, `objectName`, `message`, `script`.

- [ ] **Step 1: Write failing MVC tests**

Create `ExecutionControllerTest.java`:

```java
package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.execution.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ExecutionController.class, ApiExceptionHandler.class})
class ExecutionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DatabaseExecutionService service;

    private static final String REQUEST = """
        {"engine":"ORACLE","tableName":"alunos","columns":[
          {"name":"id","type":"BIGINT","primaryKey":true,"notNull":true,"unique":false}
        ]}
        """;

    @Test
    void executesGeneratedOracleTable() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any())).thenReturn(
            new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                "ALUNOS",
                "Tabela ALUNOS criada com sucesso no Oracle.",
                "CREATE TABLE alunos (id NUMBER(19) PRIMARY KEY NOT NULL);"
            )
        );

        mvc.perform(post("/api/v1/executions")
                .contentType("application/json")
                .content(REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.objectName").value("ALUNOS"))
            .andExpect(jsonPath("$.message").value("Tabela ALUNOS criada com sucesso no Oracle."))
            .andExpect(jsonPath("$.script").exists())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dbeduca123"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:oracle"))));
    }

    @Test
    void returns409WhenTableAlreadyExists() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new TableAlreadyExistsException(DatabaseEngine.ORACLE, "ALUNOS"));

        mvc.perform(post("/api/v1/executions")
                .contentType("application/json")
                .content(REQUEST))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.message").value("A tabela ALUNOS já existe no Oracle."));
    }

    @Test
    void returns400WhenExecutionIsNotEnabledForEngine() throws Exception {
        String postgresRequest = REQUEST.replace("ORACLE", "POSTGRESQL");
        when(service.execute(eq(DatabaseEngine.POSTGRESQL), any()))
            .thenThrow(new IllegalArgumentException("Execução ainda não habilitada para POSTGRESQL."));

        mvc.perform(post("/api/v1/executions")
                .contentType("application/json")
                .content(postgresRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Execução ainda não habilitada para POSTGRESQL."));
    }

    @Test
    void returns503WhenOracleIsUnavailable() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new DatabaseUnavailableException(DatabaseEngine.ORACLE, new RuntimeException("down")));

        mvc.perform(post("/api/v1/executions")
                .contentType("application/json")
                .content(REQUEST))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.message").value("Oracle temporariamente indisponível. Verifique o ambiente do laboratório."));
    }

    @Test
    void returns500WithoutLeakingJdbcDetailsOnUnexpectedExecutionFailure() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new DatabaseExecutionException(
                DatabaseEngine.ORACLE,
                new RuntimeException("jdbc:oracle:thin:@//secret-host:1521/FREEPDB1 password=secret")
            ));

        mvc.perform(post("/api/v1/executions")
                .contentType("application/json")
                .content(REQUEST))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.message").value("Não foi possível executar a operação no Oracle."))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-host"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password=secret"))));
    }
}
```

- [ ] **Step 2: Run MVC test and verify RED**

```powershell
mvn -Dtest=ExecutionControllerTest test
```

Expected: compilation failure because request/response/controller do not exist.

- [ ] **Step 3: Create ExecuteDatabaseRequest**

```java
package com.dbeduca.api;

import com.dbeduca.core.ColumnDefinition;
import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.core.TableDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExecuteDatabaseRequest(
    @NotNull DatabaseEngine engine,
    @NotBlank String tableName,
    @NotEmpty List<@Valid ColumnRequest> columns
) {
    public TableDefinition toDomain() {
        return new TableDefinition(tableName, columns.stream().map(ColumnRequest::toDomain).toList());
    }

    public record ColumnRequest(
        @NotBlank String name,
        @NotBlank String type,
        boolean primaryKey,
        boolean notNull,
        boolean unique
    ) {
        ColumnDefinition toDomain() {
            return new ColumnDefinition(name, type, primaryKey, notNull, unique);
        }
    }
}
```

This intentionally mirrors the existing generation request in this increment; do not refactor the public generation contract while adding execution.

- [ ] **Step 4: Create response and controller**

`ExecuteDatabaseResponse.java`:

```java
package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.execution.ExecutionResult;

public record ExecuteDatabaseResponse(
    DatabaseEngine engine,
    String status,
    String objectType,
    String objectName,
    String message,
    String script
) {
    static ExecuteDatabaseResponse from(ExecutionResult result) {
        return new ExecuteDatabaseResponse(
            result.engine(),
            result.status(),
            result.objectType(),
            result.objectName(),
            result.message(),
            result.script()
        );
    }
}
```

`ExecutionController.java`:

```java
package com.dbeduca.api;

import com.dbeduca.execution.DatabaseExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {
    private final DatabaseExecutionService service;

    public ExecutionController(DatabaseExecutionService service) {
        this.service = service;
    }

    @PostMapping
    public ExecuteDatabaseResponse execute(@Valid @RequestBody ExecuteDatabaseRequest request) {
        return ExecuteDatabaseResponse.from(
            service.execute(request.engine(), request.toDomain())
        );
    }
}
```

- [ ] **Step 5: Extend ApiExceptionHandler with execution-safe responses**

Add imports:

```java
import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.execution.DatabaseExecutionException;
import com.dbeduca.execution.DatabaseUnavailableException;
import com.dbeduca.execution.TableAlreadyExistsException;
```

Add handlers before the record declarations:

```java
@ExceptionHandler(TableAlreadyExistsException.class)
@ResponseStatus(HttpStatus.CONFLICT)
ExecutionErrorResponse tableAlreadyExists(TableAlreadyExistsException ex) {
    return new ExecutionErrorResponse(Instant.now(), ex.getMessage(), ex.engine());
}

@ExceptionHandler(DatabaseUnavailableException.class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
ExecutionErrorResponse databaseUnavailable(DatabaseUnavailableException ex) {
    return new ExecutionErrorResponse(Instant.now(), ex.getMessage(), ex.engine());
}

@ExceptionHandler(DatabaseExecutionException.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
ExecutionErrorResponse executionFailure(DatabaseExecutionException ex) {
    return new ExecutionErrorResponse(Instant.now(), ex.getMessage(), ex.engine());
}

public record ExecutionErrorResponse(
    Instant timestamp,
    String message,
    DatabaseEngine engine
) {}
```

Keep the existing `ErrorResponse(Instant timestamp, String message)` for validation and generic illegal arguments so current API behavior does not change.

- [ ] **Step 6: Run MVC tests and verify GREEN**

```powershell
mvn -Dtest=ExecutionControllerTest test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 7: Run all backend tests**

```powershell
mvn clean test
```

Expected: all original 7 tests plus new service/adapter/controller tests pass.

- [ ] **Step 8: Commit Task 4**

```powershell
git add backend/src/main/java/com/dbeduca/api backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java
git commit -m "feat: expose controlled Oracle execution endpoint"
```

---

### Task 5: Integrar o botão “Executar no Oracle” no React

**Files:**
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `POST /api/v1/executions`.
- Produces: `api.executeDatabase(payload)` and UI states `executing`, `executionResult`, `executionError`.

- [ ] **Step 1: Add the frontend API method**

In `frontend/src/api.js`, extend `api`:

```javascript
executeDatabase: payload => request('/api/v1/executions', {
  method: 'POST',
  body: JSON.stringify(payload)
})
```

The final object becomes:

```javascript
export const api = {
  listEngines: () => request('/api/v1/platform/engines'),
  generateScript: payload => request('/api/v1/scripts/generate', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  executeDatabase: payload => request('/api/v1/executions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
```

- [ ] **Step 2: Add execution state to App.jsx**

Immediately after existing `loading` state, add:

```javascript
const [executing, setExecuting] = useState(false)
const [executionResult, setExecutionResult] = useState(null)
const [executionError, setExecutionError] = useState('')
```

Add a helper that invalidates the reviewed script whenever the model changes:

```javascript
function invalidateGeneratedState() {
  setScript('')
  setExecutionResult(null)
  setExecutionError('')
}
```

- [ ] **Step 3: Invalidate stale scripts when the model changes**

Update `updateColumn`:

```javascript
function updateColumn(index, field, value) {
  setColumns(current => current.map((column, i) => i === index ? { ...column, [field]: value } : column))
  invalidateGeneratedState()
}
```

Update `removeColumn`:

```javascript
function removeColumn(index) {
  setColumns(current => current.length === 1 ? current : current.filter((_, i) => i !== index))
  invalidateGeneratedState()
}
```

Replace the engine-card click with:

```javascript
onClick={() => {
  setEngine(item.id)
  invalidateGeneratedState()
}}
```

Replace table-name `onChange` with:

```javascript
onChange={e => {
  setTableName(e.target.value)
  invalidateGeneratedState()
}}
```

Replace the `+ Campo` handler with:

```javascript
onClick={() => {
  setColumns(current => [...current, newColumn()])
  invalidateGeneratedState()
}}
```

This is a safety invariant: the user can only execute a model after regenerating and reviewing its current script.

- [ ] **Step 4: Reset execution feedback on generation and add executeOracle()**

At the start of `generate()` add:

```javascript
setExecutionResult(null)
setExecutionError('')
```

Add:

```javascript
async function executeOracle() {
  if (engine !== 'ORACLE' || !script) return

  setExecuting(true)
  setExecutionResult(null)
  setExecutionError('')

  try {
    const result = await api.executeDatabase({ engine, tableName, columns })
    setExecutionResult(result)
  } catch (err) {
    setExecutionError(err.message)
  } finally {
    setExecuting(false)
  }
}
```

- [ ] **Step 5: Render the Oracle execution action and feedback**

Immediately after `.code-window`, add:

```jsx
{engine === 'ORACLE' && script && (
  <div className="execution-area">
    <button
      className="execute-button"
      onClick={executeOracle}
      disabled={executing}
    >
      {executing ? 'Executando…' : 'Executar no Oracle'}
    </button>

    {executionResult && (
      <div className="execution-feedback success">
        <strong>Execução concluída</strong>
        <p>{executionResult.message}</p>
      </div>
    )}

    {executionError && (
      <div className="execution-feedback error">
        <strong>Execução não realizada</strong>
        <p>{executionError}</p>
      </div>
    )}
  </div>
)}
```

Update the safety-note copy to:

```jsx
<div className="safety-note">
  <strong>Modo seguro do laboratório</strong>
  <p>
    O Oracle executa somente o CREATE TABLE regenerado e validado pelo backend.
    SQL livre, DROP automático e credenciais no navegador continuam desabilitados.
  </p>
</div>
```

- [ ] **Step 6: Add focused styles without changing the theme architecture**

Append to `frontend/src/styles.css` using existing CSS variables rather than fixed theme-specific colors where possible:

```css
.execution-area {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.execute-button {
  width: 100%;
  border: 0;
  border-radius: 10px;
  padding: 12px 16px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.execute-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.execution-feedback {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 14px;
}

.execution-feedback p {
  margin: 4px 0 0;
}

.execution-feedback.success {
  background: color-mix(in srgb, #22c55e 12%, var(--panel));
}

.execution-feedback.error {
  background: color-mix(in srgb, #ef4444 10%, var(--panel));
}
```

If the existing stylesheet uses different variable names than `--border` or `--panel`, reuse the equivalent existing variables instead of introducing a parallel theme system.

- [ ] **Step 7: Build the frontend**

From `frontend/`:

```powershell
npm install
npm run build
```

Expected: Vite build completes without errors and produces `dist/`.

- [ ] **Step 8: Commit Task 5**

```powershell
git add frontend/src/api.js frontend/src/App.jsx frontend/src/styles.css
git commit -m "feat: add controlled Oracle execution action"
```

---

### Task 6: Documentar a execução Oracle e validar o fluxo real ponta a ponta

**Files:**
- Modify: `README.md`
- Verify: `docker-compose.yml`, backend API, Oracle container, frontend.

**Interfaces:**
- Consumes: complete Tasks 1–5.
- Produces: documented commands and evidence that a table can be created once and receives `409` on the second attempt.

- [ ] **Step 1: Update README status and endpoint documentation**

Document explicitly:

```markdown
## Execução controlada no Oracle

O Sprint 2 introduz a primeira execução real do DBEduca. Nesta etapa, somente o Oracle aceita execução e apenas para `CREATE TABLE` regenerado pelo backend a partir do modelo validado.

- SQL livre: desabilitado;
- `DROP TABLE` automático: desabilitado;
- tabela existente: `409 Conflict`;
- credenciais: somente no backend;
- JDBC local: `jdbc:oracle:thin:@//localhost:51521/FREEPDB1`;
- JDBC no Compose: `jdbc:oracle:thin:@//lab-oracle:1521/FREEPDB1`.

### Endpoint

`POST /api/v1/executions`
```

In the roadmap, mark Oracle controlled `CREATE TABLE` execution as completed while leaving the general editor and other engines unchecked.

- [ ] **Step 2: Verify Docker Compose syntax**

From repository root:

```powershell
docker compose config
```

Expected: exit code 0 and services include `lab-oracle`, `backend`, `frontend`.

- [ ] **Step 3: Start and verify Oracle health**

```powershell
docker compose up -d lab-oracle
docker compose ps lab-oracle
```

Expected: `STATUS` eventually contains `(healthy)`.

- [ ] **Step 4: Set local backend Oracle environment**

In the PowerShell terminal used to start Spring Boot:

```powershell
$env:ORACLE_JDBC_URL="jdbc:oracle:thin:@//localhost:51521/FREEPDB1"
$env:ORACLE_USERNAME="dbeduca"
$env:ORACLE_PASSWORD="dbeduca123"
```

Then start the backend:

```powershell
cd backend
mvn spring-boot:run
```

Expected: application starts on port `8080` without requiring an Oracle connection during startup.

- [ ] **Step 5: Verify the health endpoint before database execution**

In a second PowerShell terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected: `status = UP`.

- [ ] **Step 6: Create a unique table through the new execution endpoint**

Use a name not previously created, for example `alunos_sprint2`:

```powershell
$body = @{
  engine = "ORACLE"
  tableName = "alunos_sprint2"
  columns = @(
    @{ name = "id"; type = "BIGINT"; primaryKey = $true; notNull = $true; unique = $false },
    @{ name = "nome"; type = "VARCHAR(100)"; primaryKey = $false; notNull = $true; unique = $false },
    @{ name = "email"; type = "VARCHAR(150)"; primaryKey = $false; notNull = $false; unique = $true }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod \
  -Uri http://localhost:8080/api/v1/executions \
  -Method Post \
  -ContentType "application/json" \
  -Body $body
```

Expected response contains:

```text
engine     : ORACLE
status     : SUCCESS
objectType : TABLE
objectName : ALUNOS_SPRINT2
```

- [ ] **Step 7: Verify the table directly inside Oracle**

```powershell
@'
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'ALUNOS_SPRINT2';
EXIT;
'@ | docker compose exec -T lab-oracle sqlplus -s "dbeduca/dbeduca123@//localhost:1521/FREEPDB1"
```

Expected: `ALUNOS_SPRINT2` appears exactly once.

- [ ] **Step 8: Execute the same request again and verify 409 without DROP**

Use `Invoke-WebRequest` to inspect the status code:

```powershell
try {
  Invoke-WebRequest \
    -Uri http://localhost:8080/api/v1/executions \
    -Method Post \
    -ContentType "application/json" \
    -Body $body
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expected: `409`.

Re-run the direct Oracle query from Step 7. Expected: `ALUNOS_SPRINT2` still exists.

- [ ] **Step 9: Verify the full automated suites and frontend build one final time**

Backend:

```powershell
cd backend
mvn clean test
```

Expected: zero failures and `BUILD SUCCESS`.

Frontend:

```powershell
cd ..\frontend
npm run build
```

Expected: Vite build exits successfully.

- [ ] **Step 10: Commit documentation**

```powershell
cd ..
git add README.md
git commit -m "docs: document controlled Oracle execution"
```

- [ ] **Step 11: Inspect final diff before opening a PR**

```powershell
git status
git diff main...HEAD --stat
git log --oneline --decorate -12
```

Expected: clean working tree after commits; changes are limited to Oracle controlled execution, its UI, config, tests and documentation.

---

## Final Verification Checklist

- [ ] Existing script generation tests remain green.
- [ ] New service tests prove DDL is regenerated in the backend before adapter invocation.
- [ ] Oracle adapter test proves existing tables are not recreated and no DDL statement is opened in that branch.
- [ ] Connection acquisition failure maps to `503`.
- [ ] Unexpected JDBC execution failure maps to generic `500` without driver details in HTTP response.
- [ ] `POST /api/v1/executions` returns `200` for valid Oracle structure.
- [ ] PostgreSQL/MySQL/MongoDB execution attempts remain rejected with `400`.
- [ ] No API response includes JDBC URL, username password, or stack trace.
- [ ] Model changes invalidate the previously reviewed script before execution.
- [ ] Frontend only displays `Executar no Oracle` when `engine === 'ORACLE'` and a current script exists.
- [ ] Oracle container is `healthy`.
- [ ] Real endpoint creates a new Oracle table.
- [ ] Repeated execution returns `409` and leaves the existing table intact.
- [ ] `mvn clean test` succeeds with zero failures/errors.
- [ ] `npm run build` succeeds.
- [ ] README documents Oracle controlled execution and environment values.
