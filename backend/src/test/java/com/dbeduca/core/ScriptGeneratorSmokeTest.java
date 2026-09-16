package com.dbeduca.core;

import java.util.List;

public class ScriptGeneratorSmokeTest {
    public static void main(String[] args) {
        var columns = List.of(
            new ColumnDefinition("id", "BIGINT", true, true, false),
            new ColumnDefinition("nome", "VARCHAR(100)", false, true, false),
            new ColumnDefinition("email", "VARCHAR(150)", false, false, true)
        );
        var table = new TableDefinition("alunos", columns);

        String pg = new PostgresScriptGenerator().generate(table);
        assertContains(pg, "CREATE TABLE alunos");
        assertContains(pg, "PRIMARY KEY");
        assertContains(pg, "UNIQUE");

        String mysql = new MySqlScriptGenerator().generate(table);
        assertContains(mysql, "CREATE TABLE alunos");
        assertContains(mysql, "VARCHAR(100)");

        String mongo = new MongoScriptGenerator().generate(table);
        assertContains(mongo, "db.createCollection(\"alunos\")");

        String oracle = new OracleScriptGenerator().generate(table);
        assertContains(oracle, "CREATE TABLE alunos");
        assertContains(oracle, "id NUMBER(19) PRIMARY KEY NOT NULL");
        assertContains(oracle, "nome VARCHAR2(100) NOT NULL");
    }

    private static void assertContains(String actual, String expected) {
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected [" + expected + "] in:\n" + actual);
        }
    }
}
