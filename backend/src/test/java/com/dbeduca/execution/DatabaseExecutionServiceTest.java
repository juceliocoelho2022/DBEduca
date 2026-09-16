package com.dbeduca.execution;

import com.dbeduca.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseExecutionServiceTest {

    private final TableDefinition table = new TableDefinition(
        "alunos",
        List.of(
            new ColumnDefinition(
                "id",
                "BIGINT",
                true,
                true,
                false
            ),
            new ColumnDefinition(
                "nome",
                "VARCHAR(100)",
                false,
                true,
                false
            )
        )
    );

    @Test
    void regeneratesOracleDdlBeforeDelegating() {

        var registry = new ScriptGeneratorRegistry(
            List.of(new OracleScriptGenerator())
        );

        var adapter = new CapturingAdapter();

        var service = new DatabaseExecutionService(
            registry,
            List.of(adapter)
        );

        var result = service.execute(
            DatabaseEngine.ORACLE,
            table
        );

        assertTrue(
            adapter.ddl.contains("CREATE TABLE alunos")
        );

        assertTrue(
            adapter.ddl.contains(
                "id NUMBER(19) PRIMARY KEY NOT NULL"
            )
        );

        assertEquals(
            DatabaseEngine.ORACLE,
            result.engine()
        );

        assertEquals(
            "ALUNOS",
            result.objectName()
        );

        assertEquals(
            adapter.ddl,
            result.script()
        );
    }

    @Test
    void rejectsEngineWithoutExecutionAdapter() {

        var registry = new ScriptGeneratorRegistry(
            List.of(new PostgresScriptGenerator())
        );

        var service = new DatabaseExecutionService(
            registry,
            List.of()
        );

        var error = assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(
                DatabaseEngine.POSTGRESQL,
                table
            )
        );

        assertEquals(
            "Execução ainda não habilitada para POSTGRESQL.",
            error.getMessage()
        );
    }

    private static final class CapturingAdapter
        implements DatabaseExecutionAdapter {

        private String ddl;

        @Override
        public DatabaseEngine engine() {
            return DatabaseEngine.ORACLE;
        }

        @Override
        public ExecutionResult executeCreateTable(
            String tableName,
            String ddl
        ) {
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
