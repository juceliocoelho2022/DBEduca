package com.dbeduca.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptGeneratorTest {
    private final TableDefinition table = new TableDefinition("alunos", List.of(
        new ColumnDefinition("id", "BIGINT", true, true, false),
        new ColumnDefinition("nome", "VARCHAR(100)", false, true, false),
        new ColumnDefinition("email", "VARCHAR(150)", false, false, true)
    ));

    @Test
    void generatesPostgresqlDdl() {
        var sql = new PostgresScriptGenerator().generate(table);
        assertTrue(sql.contains("CREATE TABLE alunos"));
        assertTrue(sql.contains("id BIGINT PRIMARY KEY NOT NULL"));
        assertTrue(sql.contains("email VARCHAR(150) UNIQUE"));
    }

    @Test
    void generatesMysqlDdl() {
        var sql = new MySqlScriptGenerator().generate(table);
        assertTrue(sql.contains("CREATE TABLE alunos"));
        assertTrue(sql.contains("ENGINE=InnoDB"));
    }

    @Test
    void generatesMongoCollectionScript() {
        var script = new MongoScriptGenerator().generate(table);
        assertTrue(script.contains("db.createCollection(\"alunos\")"));
        assertTrue(script.contains("nome"));
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> new TableDefinition("alunos; DROP TABLE usuarios", table.columns()));
    }
}
