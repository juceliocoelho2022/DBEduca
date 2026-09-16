package com.dbeduca.execution;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OracleExecutionAdapterTest {

    private static final String DDL =
        "CREATE TABLE alunos (id NUMBER(19) PRIMARY KEY NOT NULL);";

    @Test
    void createsTableWhenItDoesNotExist() throws Exception {

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement ddlStatement = mock(Statement.class);

        when(dataSource.getConnection())
            .thenReturn(connection);

        when(connection.prepareStatement(anyString()))
            .thenReturn(existsStatement);

        when(existsStatement.executeQuery())
            .thenReturn(resultSet);

        when(resultSet.next())
            .thenReturn(true);

        when(resultSet.getInt(1))
            .thenReturn(0);

        when(connection.createStatement())
            .thenReturn(ddlStatement);

        var adapter =
            new OracleExecutionAdapter(dataSource);

        var result =
            adapter.executeCreateTable("alunos", DDL);

        verify(existsStatement)
            .setString(1, "ALUNOS");

        verify(ddlStatement)
            .executeUpdate(DDL);

        assertEquals(
            "SUCCESS",
            result.status()
        );

        assertEquals(
            "ALUNOS",
            result.objectName()
        );

        assertEquals(
            DDL,
            result.script()
        );
    }

    @Test
    void refusesExistingTableWithoutExecutingDdl() throws Exception {

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection())
            .thenReturn(connection);

        when(connection.prepareStatement(anyString()))
            .thenReturn(existsStatement);

        when(existsStatement.executeQuery())
            .thenReturn(resultSet);

        when(resultSet.next())
            .thenReturn(true);

        when(resultSet.getInt(1))
            .thenReturn(1);

        var adapter =
            new OracleExecutionAdapter(dataSource);

        var error = assertThrows(
            TableAlreadyExistsException.class,
            () -> adapter.executeCreateTable(
                "alunos",
                DDL
            )
        );

        assertEquals(
            "ALUNOS",
            error.objectName()
        );

        verify(
            connection,
            never()
        ).createStatement();
    }

    @Test
    void mapsConnectionFailureToDatabaseUnavailable() throws Exception {

        DataSource dataSource = mock(DataSource.class);

        when(dataSource.getConnection())
            .thenThrow(
                new SQLException("connection refused")
            );

        var adapter =
            new OracleExecutionAdapter(dataSource);

        var error = assertThrows(
            DatabaseUnavailableException.class,
            () -> adapter.executeCreateTable(
                "alunos",
                DDL
            )
        );

        assertEquals(
            com.dbeduca.core.DatabaseEngine.ORACLE,
            error.engine()
        );
    }

    @Test
    void mapsDdlFailureToDatabaseExecutionException() throws Exception {

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement existsStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Statement ddlStatement = mock(Statement.class);

        when(dataSource.getConnection())
            .thenReturn(connection);

        when(connection.prepareStatement(anyString()))
            .thenReturn(existsStatement);

        when(existsStatement.executeQuery())
            .thenReturn(resultSet);

        when(resultSet.next())
            .thenReturn(true);

        when(resultSet.getInt(1))
            .thenReturn(0);

        when(connection.createStatement())
            .thenReturn(ddlStatement);

        when(ddlStatement.executeUpdate(DDL))
            .thenThrow(
                new SQLException("ORA-00955")
            );

        var adapter =
            new OracleExecutionAdapter(dataSource);

        var error = assertThrows(
            DatabaseExecutionException.class,
            () -> adapter.executeCreateTable(
                "alunos",
                DDL
            )
        );

        assertEquals(
            com.dbeduca.core.DatabaseEngine.ORACLE,
            error.engine()
        );

        assertEquals(
            "ALUNOS",
            error.objectName()
        );
    }
}