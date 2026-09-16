package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.execution.DatabaseExecutionService;
import com.dbeduca.execution.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({
    ExecutionController.class,
    ApiExceptionHandler.class
})
class ExecutionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DatabaseExecutionService executionService;

    @Test
    void executesOracleTableCreation() throws Exception {

        when(
            executionService.execute(
                eq(DatabaseEngine.ORACLE),
                any()
            )
        ).thenReturn(
            new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                "ALUNOS",
                "Tabela ALUNOS criada com sucesso no Oracle.",
                "CREATE TABLE alunos (...);"
            )
        );

        mvc.perform(
                post("/api/v1/executions")
                    .contentType("application/json")
                    .content("""
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
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("ORACLE"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.objectType").value("TABLE"))
            .andExpect(jsonPath("$.objectName").value("ALUNOS"))
            .andExpect(
                jsonPath("$.message")
                    .value("Tabela ALUNOS criada com sucesso no Oracle.")
            )
            .andExpect(
                jsonPath("$.script")
                    .value("CREATE TABLE alunos (...);")
            );
    }

    @Test
    void returnsConflictWhenTableAlreadyExists() throws Exception {

        when(
            executionService.execute(
                eq(DatabaseEngine.ORACLE),
                any()
            )
        ).thenThrow(
            new com.dbeduca.execution.TableAlreadyExistsException(
                DatabaseEngine.ORACLE,
                "ALUNOS"
            )
        );

        mvc.perform(
                post("/api/v1/executions")
                    .contentType("application/json")
                    .content("""
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
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.message")
                    .value("A tabela ALUNOS já existe no Oracle.")
            );
    }

    @Test
    void returnsBadRequestWhenExecutionEngineIsNotEnabled() throws Exception {

        when(
            executionService.execute(
                eq(DatabaseEngine.POSTGRESQL),
                any()
            )
        ).thenThrow(
            new IllegalArgumentException(
                "Execução ainda não habilitada para POSTGRESQL."
            )
        );

        mvc.perform(
                post("/api/v1/executions")
                    .contentType("application/json")
                    .content("""
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
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Execução ainda não habilitada para POSTGRESQL."
                    )
            );
    }

    @Test
    void returnsServiceUnavailableWhenOracleIsDown() throws Exception {

        when(
            executionService.execute(
                eq(DatabaseEngine.ORACLE),
                any()
            )
        ).thenThrow(
            new com.dbeduca.execution.DatabaseUnavailableException(
                DatabaseEngine.ORACLE,
                new java.sql.SQLException("connection refused")
            )
        );

        mvc.perform(
                post("/api/v1/executions")
                    .contentType("application/json")
                    .content("""
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
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Oracle temporariamente indisponível. Verifique o ambiente do laboratório."
                    )
            );
    }

    @Test
    void returnsGenericInternalServerErrorWithoutLeakingDatabaseDetails() throws Exception {

        when(
            executionService.execute(
                eq(DatabaseEngine.ORACLE),
                any()
            )
        ).thenThrow(
            new com.dbeduca.execution.DatabaseExecutionException(
                DatabaseEngine.ORACLE,
                "ALUNOS",
                new java.sql.SQLException(
                    "ORA-00999 jdbc:oracle:thin:@//secret-host:1521/FREEPDB1 " +
                    "user=dbeduca password=supersecret"
                )
            )
        );

        mvc.perform(
                post("/api/v1/executions")
                    .contentType("application/json")
                    .content("""
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
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isInternalServerError())
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Falha ao executar operação no banco de dados."
                    )
            )
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host")
                    )
                )
            )
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("supersecret")
                    )
                )
            )
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ORA-00999")
                    )
                )
            );
    }
}