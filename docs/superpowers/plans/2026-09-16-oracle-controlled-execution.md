# Oracle Controlled Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que o DBEduca execute, via JDBC e de forma controlada, o `CREATE TABLE` Oracle gerado pelo próprio backend a partir do modelo validado pelo aluno, sem aceitar SQL livre e sem apagar tabelas existentes.

**Architecture:** O `ExecutionController` recebe a mesma estrutura usada na geração de scripts e delega ao `DatabaseExecutionService`. O serviço regenera o DDL com `ScriptGeneratorRegistry`, seleciona um `DatabaseExecutionAdapter` e, nesta etapa, somente `OracleExecutionAdapter` está registrado. O adapter consulta `USER_TABLES` com bind parameter e executa o DDL via JDBC apenas quando a tabela ainda não existe.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Web MVC, Jakarta Validation, Oracle JDBC Thin (`ojdbc11`), JDBC (`java.sql`/`javax.sql.DataSource`), JUnit 5, Mockito, MockMvc, React 19.3, Vite 8.2.2, Docker Compose, Oracle Database Free.

**Spec:** `docs/superpowers/specs/2026-09-16-oracle-controlled-execution-design.md`

## Global Constraints

- A primeira engine com execução real é `ORACLE`.
- O navegador nunca envia SQL arbitrário para execução.
- O backend regenera o DDL a partir de `TableDefinition` usando `ScriptGeneratorRegistry`.
- Somente `CREATE TABLE` produzido pelo `OracleScriptGenerator` pode chegar ao adapter nesta etapa.
- Tabela existente retorna `409 Conflict`; não executar `DROP TABLE` ou recriação automática.
- PostgreSQL, MySQL e MongoDB permanecem apenas com geração de script.
- O backend usa o usuário de aplicação `dbeduca`, nunca `SYS` ou `SYSTEM`.
- O Compose já reserva `ORACLE_PASSWORD` para a senha administrativa do container. Para evitar colisão, o JDBC do backend usa `ORACLE_APP_USER_PASSWORD` como senha do usuário `dbeduca`.
- A aplicação deve iniciar mesmo se o Oracle estiver fora do ar; a conexão é aberta somente durante a execução.
- Qualquer alteração em engine, nome da tabela ou colunas invalida o script previamente gerado e o resultado da execução, garantindo que o SQL revisado corresponda ao modelo executado.
- Manter tema Light/Dark e layout atual.
- TDD obrigatório para código Java: RED → GREEN → refactor.

---

## File Map

**Create:**

- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionAdapter.java`
- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionService.java`
- `backend/src/main/java/com/dbeduca/execution/ExecutionResult.java`
- `backend/src/main/java/com/dbeduca/execution/TableAlreadyExistsException.java`
- `backend/src/main/java/com/dbeduca/execution/DatabaseUnavailableException.java`
- `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionException.java`
- `backend/src/main/java/com/dbeduca/execution/OracleExecutionAdapter.java`
- `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseRequest.java`
- `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseResponse.java`
- `backend/src/main/java/com/dbeduca/api/ExecutionController.java`
- `backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java`
- `backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java`
- `backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java`

**Modify:**

- `backend/pom.xml`
- `backend/src/main/java/com/dbeduca/config/ApplicationConfig.java`
- `backend/src/main/java/com/dbeduca/api/ApiExceptionHandler.java`
- `backend/src/main/resources/application.properties`
- `.env.example`
- `docker-compose.yml`
- `frontend/src/api.js`
- `frontend/src/App.jsx`
- `frontend/src/styles.css`
- `README.md`

---

### Task 1: Contrato de execução e serviço coordenador

**Files:**
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionAdapter.java`
- Create: `backend/src/main/java/com/dbeduca/execution/ExecutionResult.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionService.java`
- Test: `backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java`

**Interfaces:**
- Consumes: `DatabaseEngine`, `TableDefinition`, `ScriptGeneratorRegistry`.
- Produces: `DatabaseExecutionAdapter.engine()`, `DatabaseExecutionAdapter.executeCreateTable(String, String)`, `DatabaseExecutionService.execute(DatabaseEngine, TableDefinition)`.

- [ ] **Step 1: Write the failing service tests**

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
    void regeneratesOracleDdlBeforeDelegating() {
        var registry = new ScriptGeneratorRegistry(List.of(new OracleScriptGenerator()));
        var adapter = new CapturingAdapter();
        var service = new DatabaseExecutionService(registry, List.of(adapter));

        var result = service.execute(DatabaseEngine.ORACLE, table);

        assertTrue(adapter.ddl.contains("CREATE TABLE alunos"));
        assertTrue(adapter.ddl.contains("id NUMBER(19) PRIMARY KEY NOT NULL"));
        assertEquals(DatabaseEngine.ORACLE, result.engine());
        assertEquals("ALUNOS", result.objectName());
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
                "ALUNOS",
                "Tabela ALUNOS criada com sucesso no Oracle.",
                ddl
            );
        }
    }
}
```

- [ ] **Step 2: Verify RED**

From `backend/`:

```powershell
mvn -Dtest=DatabaseExecutionServiceTest test
```

Expected: compilation fails because `com.dbeduca.execution` does not exist yet.

- [ ] **Step 3: Add the minimal production types**

`DatabaseExecutionAdapter.java`:

```java
package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public interface DatabaseExecutionAdapter {
    DatabaseEngine engine();
    ExecutionResult executeCreateTable(String tableName, String ddl);
}
```

`ExecutionResult.java`:

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

`DatabaseExecutionService.java`:

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

    public DatabaseExecutionService(ScriptGeneratorRegistry scriptRegistry, List<DatabaseExecutionAdapter> adapters) {
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

- [ ] **Step 4: Verify GREEN and regression**

```powershell
mvn -Dtest=DatabaseExecutionServiceTest,ScriptGeneratorTest test
```

Expected: zero failures/errors.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/dbeduca/execution backend/src/test/java/com/dbeduca/execution/DatabaseExecutionServiceTest.java
git commit -m "feat: add database execution service contract"
```

---

### Task 2: OracleExecutionAdapter com JDBC e proteção de tabela existente

**Files:**
- Create: `backend/src/main/java/com/dbeduca/execution/TableAlreadyExistsException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseUnavailableException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/DatabaseExecutionException.java`
- Create: `backend/src/main/java/com/dbeduca/execution/OracleExecutionAdapter.java`
- Test: `backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java`

**Interfaces:**
- Consumes: `javax.sql.DataSource`.
- Produces: `OracleExecutionAdapter(DataSource)`.

- [ ] **Step 1: Write failing adapter tests**

```java
package com.dbeduca.execution;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OracleExecutionAdapterTest {
    private static final String DDL = "CREATE TABLE alunos (id NUMBER(19) PRIMARY KEY NOT NULL);";

    @Test
    void createsTableWhenItDoesNotExist() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement exists = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        Statement ddlStatement = mock(Statement.class);

        when(ds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(exists);
        when(exists.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(0);
        when(connection.createStatement()).thenReturn(ddlStatement);

        var result = new OracleExecutionAdapter(ds).executeCreateTable("alunos", DDL);

        verify(exists).setString(1, "ALUNOS");
        verify(ddlStatement).executeUpdate(DDL);
        assertEquals("SUCCESS", result.status());
        assertEquals("ALUNOS", result.objectName());
        assertEquals(DDL, result.script());
    }

    @Test
    void refusesExistingTableWithoutOpeningDdlStatement() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement exists = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(exists);
        when(exists.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(1);

        var adapter = new OracleExecutionAdapter(ds);
        var error = assertThrows(TableAlreadyExistsException.class,
            () -> adapter.executeCreateTable("alunos", DDL));

        assertEquals("ALUNOS", error.objectName());
        verify(connection, never()).createStatement();
    }

    @Test
    void mapsConnectionFailureToDatabaseUnavailable() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        var error = assertThrows(DatabaseUnavailableException.class,
            () -> new OracleExecutionAdapter(ds).executeCreateTable("alunos", DDL));

        assertEquals(com.dbeduca.core.DatabaseEngine.ORACLE, error.engine());
    }
}
```

- [ ] **Step 2: Verify RED**

```powershell
mvn -Dtest=OracleExecutionAdapterTest test
```

Expected: compilation fails because adapter/exceptions do not exist.

- [ ] **Step 3: Add domain exceptions**

`TableAlreadyExistsException.java`:

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

`DatabaseUnavailableException.java`:

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

`DatabaseExecutionException.java`:

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

- [ ] **Step 4: Implement OracleExecutionAdapter**

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
        String normalized = tableName.toUpperCase(Locale.ROOT);
        try (Connection connection = openConnection()) {
            if (tableExists(connection, normalized)) {
                throw new TableAlreadyExistsException(DatabaseEngine.ORACLE, normalized);
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(ddl);
            }
            return new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                normalized,
                "Tabela " + normalized + " criada com sucesso no Oracle.",
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

- [ ] **Step 5: Verify GREEN**

```powershell
mvn -Dtest=OracleExecutionAdapterTest,DatabaseExecutionServiceTest,ScriptGeneratorTest test
```

Expected: zero failures/errors.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/dbeduca/execution backend/src/test/java/com/dbeduca/execution/OracleExecutionAdapterTest.java
git commit -m "feat: execute generated Oracle DDL safely"
```

---

### Task 3: Configuração JDBC sem conexão no startup

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/dbeduca/config/ApplicationConfig.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `.env.example`
- Modify: `docker-compose.yml`

**Interfaces:**
- Host/manual backend: `ORACLE_JDBC_URL`, `ORACLE_USERNAME`, `ORACLE_APP_USER_PASSWORD`.
- Container Oracle admin password remains `ORACLE_PASSWORD`; do not reuse it for JDBC da aplicação.

- [ ] **Step 1: Add Oracle JDBC driver**

Inside `<dependencies>` in `backend/pom.xml`:

```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
</dependency>
```

- [ ] **Step 2: Verify dependency resolution**

```powershell
mvn -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Add Spring properties**

Append to `backend/src/main/resources/application.properties`:

```properties
dbeduca.oracle.jdbc-url=${ORACLE_JDBC_URL:jdbc:oracle:thin:@//localhost:51521/FREEPDB1}
dbeduca.oracle.username=${ORACLE_USERNAME:dbeduca}
dbeduca.oracle.password=${ORACLE_APP_USER_PASSWORD:}
```

- [ ] **Step 4: Wire DataSource and execution beans**

Add imports to `ApplicationConfig.java`:

```java
import com.dbeduca.execution.DatabaseExecutionAdapter;
import com.dbeduca.execution.DatabaseExecutionService;
import com.dbeduca.execution.OracleExecutionAdapter;
import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.SQLException;
```

Keep `scriptGeneratorRegistry()` unchanged and add:

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

`OracleDataSource` is configured at startup but does not call `getConnection()` until `OracleExecutionAdapter.executeCreateTable(...)`.

- [ ] **Step 5: Update `.env.example` without credential collision**

Ensure this block exists:

```dotenv
ORACLE_PASSWORD=oracle123
ORACLE_APP_USER=dbeduca
ORACLE_APP_USER_PASSWORD=dbeduca123
ORACLE_JDBC_URL=jdbc:oracle:thin:@//localhost:51521/FREEPDB1
ORACLE_USERNAME=dbeduca
```

- [ ] **Step 6: Configure backend service in Compose**

Under `backend:` add:

```yaml
environment:
  ORACLE_JDBC_URL: jdbc:oracle:thin:@//lab-oracle:1521/FREEPDB1
  ORACLE_USERNAME: ${ORACLE_APP_USER:-dbeduca}
  ORACLE_APP_USER_PASSWORD: ${ORACLE_APP_USER_PASSWORD:-dbeduca123}
```

Do not add a hard dependency on Oracle health; the API must return `503` if Oracle is unavailable at execution time.

- [ ] **Step 7: Run full backend tests**

```powershell
mvn clean test
```

Expected: `BUILD SUCCESS`, zero failures/errors.

- [ ] **Step 8: Commit**

```powershell
git add backend/pom.xml backend/src/main/java/com/dbeduca/config/ApplicationConfig.java backend/src/main/resources/application.properties .env.example docker-compose.yml
git commit -m "chore: configure Oracle JDBC execution"
```

---

### Task 4: Endpoint REST e mapeamento de erros

**Files:**
- Create: `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseRequest.java`
- Create: `backend/src/main/java/com/dbeduca/api/ExecuteDatabaseResponse.java`
- Create: `backend/src/main/java/com/dbeduca/api/ExecutionController.java`
- Modify: `backend/src/main/java/com/dbeduca/api/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java`

**Interfaces:**
- Endpoint: `POST /api/v1/executions`.
- Success fields: `engine`, `status`, `objectType`, `objectName`, `message`, `script`.

- [ ] **Step 1: Write failing MVC tests**

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
            new ExecutionResult(DatabaseEngine.ORACLE, "SUCCESS", "TABLE", "ALUNOS",
                "Tabela ALUNOS criada com sucesso no Oracle.",
                "CREATE TABLE alunos (id NUMBER(19) PRIMARY KEY NOT NULL);")
        );

        mvc.perform(post("/api/v1/executions").contentType("application/json").content(REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.objectName").value("ALUNOS"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dbeduca123"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:oracle"))));
    }

    @Test
    void returns409WhenTableExists() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new TableAlreadyExistsException(DatabaseEngine.ORACLE, "ALUNOS"));

        mvc.perform(post("/api/v1/executions").contentType("application/json").content(REQUEST))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.message").value("A tabela ALUNOS já existe no Oracle."));
    }

    @Test
    void returns400ForEngineWithoutExecution() throws Exception {
        String request = REQUEST.replace("ORACLE", "POSTGRESQL");
        when(service.execute(eq(DatabaseEngine.POSTGRESQL), any()))
            .thenThrow(new IllegalArgumentException("Execução ainda não habilitada para POSTGRESQL."));

        mvc.perform(post("/api/v1/executions").contentType("application/json").content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Execução ainda não habilitada para POSTGRESQL."));
    }

    @Test
    void returns503WhenOracleIsUnavailable() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new DatabaseUnavailableException(DatabaseEngine.ORACLE, new RuntimeException("down")));

        mvc.perform(post("/api/v1/executions").contentType("application/json").content(REQUEST))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.engine").value("ORACLE"));
    }

    @Test
    void returnsGeneric500WithoutJdbcDetails() throws Exception {
        when(service.execute(eq(DatabaseEngine.ORACLE), any()))
            .thenThrow(new DatabaseExecutionException(DatabaseEngine.ORACLE,
                new RuntimeException("jdbc:oracle:thin:@//secret:1521/FREEPDB1 password=secret")));

        mvc.perform(post("/api/v1/executions").contentType("application/json").content(REQUEST))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Não foi possível executar a operação no Oracle."))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret:1521"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password=secret"))));
    }
}
```

- [ ] **Step 2: Verify RED**

```powershell
mvn -Dtest=ExecutionControllerTest test
```

Expected: compilation fails because API execution classes do not exist.

- [ ] **Step 3: Add ExecuteDatabaseRequest**

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

- [ ] **Step 4: Add response and controller**

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
        return new ExecuteDatabaseResponse(result.engine(), result.status(), result.objectType(),
            result.objectName(), result.message(), result.script());
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
        return ExecuteDatabaseResponse.from(service.execute(request.engine(), request.toDomain()));
    }
}
```

- [ ] **Step 5: Extend ApiExceptionHandler**

Add these handlers while keeping the existing validation/`IllegalArgumentException` behavior:

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

public record ExecutionErrorResponse(Instant timestamp, String message, DatabaseEngine engine) {}
```

Add imports for `DatabaseEngine` and the three execution exceptions.

- [ ] **Step 6: Verify GREEN and full backend regression**

```powershell
mvn -Dtest=ExecutionControllerTest test
mvn clean test
```

Expected: all tests pass with zero failures/errors.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/dbeduca/api backend/src/test/java/com/dbeduca/api/ExecutionControllerTest.java
git commit -m "feat: expose controlled Oracle execution endpoint"
```

---

### Task 5: Botão “Executar no Oracle” no React

**Files:**
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `POST /api/v1/executions`.
- Produces: `api.executeDatabase(payload)` and UI states `executing`, `executionResult`, `executionError`.

- [ ] **Step 1: Add API method**

Final `api` object in `frontend/src/api.js`:

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

- [ ] **Step 2: Include Oracle in frontend fallback and add execution state**

Change fallback engines to:

```javascript
const fallbackEngines = [
  { id: 'POSTGRESQL', name: 'PostgreSQL', category: 'SQL relacional' },
  { id: 'MYSQL', name: 'MySQL', category: 'SQL relacional' },
  { id: 'MONGODB', name: 'MongoDB', category: 'NoSQL documental' },
  { id: 'ORACLE', name: 'Oracle', category: 'SQL relacional' }
]
```

After existing `loading` state:

```javascript
const [executing, setExecuting] = useState(false)
const [executionResult, setExecutionResult] = useState(null)
const [executionError, setExecutionError] = useState('')
```

Add:

```javascript
function invalidateGeneratedState() {
  setScript('')
  setExecutionResult(null)
  setExecutionError('')
}
```

- [ ] **Step 3: Invalidate stale reviewed SQL on every model change**

Update `updateColumn`, `removeColumn`, engine selection, table-name change and `+ Campo` so each model mutation calls `invalidateGeneratedState()` immediately after updating state.

Exact handlers:

```javascript
function updateColumn(index, field, value) {
  setColumns(current => current.map((column, i) => i === index ? { ...column, [field]: value } : column))
  invalidateGeneratedState()
}

function removeColumn(index) {
  setColumns(current => current.length === 1 ? current : current.filter((_, i) => i !== index))
  invalidateGeneratedState()
}
```

Engine:

```jsx
onClick={() => {
  setEngine(item.id)
  invalidateGeneratedState()
}}
```

Table name:

```jsx
onChange={e => {
  setTableName(e.target.value)
  invalidateGeneratedState()
}}
```

Add column:

```jsx
onClick={() => {
  setColumns(current => [...current, newColumn()])
  invalidateGeneratedState()
}}
```

- [ ] **Step 4: Add controlled execution action**

At the beginning of `generate()` add:

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

Render immediately below `.code-window`:

```jsx
{engine === 'ORACLE' && script && (
  <div className="execution-area">
    <button className="execute-button" onClick={executeOracle} disabled={executing}>
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

Replace safety copy with:

```jsx
<div className="safety-note">
  <strong>Modo seguro do laboratório</strong>
  <p>O Oracle executa somente o CREATE TABLE regenerado e validado pelo backend. SQL livre, DROP automático e credenciais no navegador continuam desabilitados.</p>
</div>
```

- [ ] **Step 5: Update exact existing styles**

Change:

```css
.engine-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:12px; margin-bottom:18px; }
```

to:

```css
.engine-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-bottom:18px; }
```

Extend engine dots:

```css
.engine-dot.postgresql { background:#3973a7; }
.engine-dot.mysql { background:#f29111; }
.engine-dot.mongodb { background:#2f9d50; }
.engine-dot.oracle { background:#e11d48; }
```

Append:

```css
.execution-area {
  display:grid;
  gap:12px;
  margin-top:16px;
}
.execute-button {
  width:100%;
  border:1px solid var(--primary);
  border-radius:10px;
  padding:12px 16px;
  background:var(--primary);
  color:white;
  font-weight:800;
}
.execution-feedback {
  border:1px solid var(--line);
  border-radius:10px;
  padding:12px 14px;
  background:var(--surface);
}
.execution-feedback p { margin:4px 0 0; }
.execution-feedback.success { box-shadow:inset 4px 0 0 #22c55e; }
.execution-feedback.error { box-shadow:inset 4px 0 0 #ef4444; }
```

Existing global `button:disabled` already handles disabled opacity/cursor; do not duplicate it.

- [ ] **Step 6: Build frontend**

From `frontend/`:

```powershell
npm install
npm run build
```

Expected: Vite exits successfully and writes `dist/`.

- [ ] **Step 7: Commit**

```powershell
git add frontend/src/api.js frontend/src/App.jsx frontend/src/styles.css
git commit -m "feat: add controlled Oracle execution action"
```

---

### Task 6: README e validação real ponta a ponta

**Files:**
- Modify: `README.md`
- Verify: Docker Compose, backend, endpoint e Oracle.

- [ ] **Step 1: Document the feature**

Add a section containing exactly these operational facts:

```markdown
## Execução controlada no Oracle

O Sprint 2 introduz a primeira execução real do DBEduca. Nesta etapa, somente o Oracle aceita execução e apenas para `CREATE TABLE` regenerado pelo backend a partir do modelo validado.

- SQL livre: desabilitado;
- `DROP TABLE` automático: desabilitado;
- tabela existente: `409 Conflict`;
- credenciais: somente no backend;
- JDBC local: `jdbc:oracle:thin:@//localhost:51521/FREEPDB1`;
- JDBC no Compose: `jdbc:oracle:thin:@//lab-oracle:1521/FREEPDB1`;
- usuário JDBC: `dbeduca` configurado por `ORACLE_USERNAME`;
- senha JDBC: `ORACLE_APP_USER_PASSWORD`.

### Endpoint

`POST /api/v1/executions`
```

In the Sprint 2 roadmap, mark only controlled Oracle `CREATE TABLE` execution as complete; editor SQL livre and other engines remain pending.

- [ ] **Step 2: Verify Compose syntax and Oracle health**

From repository root:

```powershell
docker compose config
docker compose up -d lab-oracle
docker compose ps lab-oracle
```

Expected: `docker compose config` exits 0 and Oracle eventually reports `(healthy)`.

- [ ] **Step 3: Start backend with app-user JDBC credentials**

```powershell
$env:ORACLE_JDBC_URL="jdbc:oracle:thin:@//localhost:51521/FREEPDB1"
$env:ORACLE_USERNAME="dbeduca"
$env:ORACLE_APP_USER_PASSWORD="dbeduca123"
cd backend
mvn spring-boot:run
```

Expected: Spring Boot starts on port `8080`. Oracle does not need to be contacted during application startup.

- [ ] **Step 4: Verify backend health from a second PowerShell**

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected: `status = UP`.

- [ ] **Step 5: Create a unique Oracle table through the API**

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

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/executions" -Method Post -ContentType "application/json" -Body $body
```

Expected fields:

```text
engine     : ORACLE
status     : SUCCESS
objectType : TABLE
objectName : ALUNOS_SPRINT2
```

- [ ] **Step 6: Verify the table directly in Oracle**

From repository root:

```powershell
@'
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'ALUNOS_SPRINT2';
EXIT;
'@ | docker compose exec -T lab-oracle sqlplus -s "dbeduca/dbeduca123@//localhost:1521/FREEPDB1"
```

Expected: `ALUNOS_SPRINT2`.

- [ ] **Step 7: Repeat the same request and verify 409**

```powershell
try {
  Invoke-WebRequest -Uri "http://localhost:8080/api/v1/executions" -Method Post -ContentType "application/json" -Body $body -ErrorAction Stop
} catch {
  [int]$_.Exception.Response.StatusCode
}
```

Expected: `409`.

Re-run the query from Step 6; expected: `ALUNOS_SPRINT2` still exists.

- [ ] **Step 8: Final automated verification**

Backend:

```powershell
cd backend
mvn clean test
```

Frontend:

```powershell
cd ..\frontend
npm run build
```

Expected: backend `BUILD SUCCESS` with zero failures/errors and frontend build success.

- [ ] **Step 9: Commit README**

```powershell
cd ..
git add README.md
git commit -m "docs: document controlled Oracle execution"
```

- [ ] **Step 10: Inspect final branch**

```powershell
git status
git diff main...HEAD --stat
git log --oneline --decorate -12
```

Expected: clean working tree; changes limited to Oracle controlled execution, tests, config, UI and documentation.

---

## Final Verification Checklist

- [ ] Existing script-generation tests remain green.
- [ ] Service test proves DDL is regenerated by the backend before adapter invocation.
- [ ] Adapter test proves existing table prevents creation of a DDL statement.
- [ ] Connection acquisition failure maps to `503`.
- [ ] Unexpected JDBC execution failure maps to generic `500` without JDBC details.
- [ ] Valid Oracle execution returns `200`.
- [ ] PostgreSQL/MySQL/MongoDB execution requests remain rejected with `400`.
- [ ] API responses contain no password, JDBC URL or stack trace.
- [ ] Model mutations invalidate the reviewed script.
- [ ] `Executar no Oracle` appears only for Oracle with a current generated script.
- [ ] Oracle is `healthy`.
- [ ] Real endpoint creates a new table.
- [ ] Repeated execution returns `409` and leaves the table intact.
- [ ] `mvn clean test` succeeds with zero failures/errors.
- [ ] `npm run build` succeeds.
- [ ] README documents the exact Oracle execution contract and credentials variables.
