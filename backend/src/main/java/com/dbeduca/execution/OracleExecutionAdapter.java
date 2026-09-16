package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public final class OracleExecutionAdapter
    implements DatabaseExecutionAdapter {

    private static final String TABLE_EXISTS_SQL =
        """
        SELECT COUNT(*)
        FROM USER_TABLES
        WHERE TABLE_NAME = ?
        """;

    private final DataSource dataSource;

    public OracleExecutionAdapter(
        DataSource dataSource
    ) {
        this.dataSource = dataSource;
    }

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.ORACLE;
    }

    @Override
    public ExecutionResult executeCreateTable(
        String tableName,
        String ddl
    ) {
        String normalized =
            tableName.toUpperCase(Locale.ROOT);

        try (Connection connection = openConnection()) {

            if (tableExists(
                connection,
                normalized
            )) {
                throw new TableAlreadyExistsException(
                    DatabaseEngine.ORACLE,
                    normalized
                );
            }

            try (var statement =
                     connection.createStatement()) {

                statement.executeUpdate(ddl);
            }

            return new ExecutionResult(
                DatabaseEngine.ORACLE,
                "SUCCESS",
                "TABLE",
                normalized,
                "Tabela " +
                    normalized +
                    " criada com sucesso no Oracle.",
                ddl
            );

        } catch (
            TableAlreadyExistsException |
            DatabaseUnavailableException ex
        ) {
            throw ex;

        } catch (SQLException ex) {
            throw new DatabaseExecutionException(
                DatabaseEngine.ORACLE,
                normalized,
                ex
            );
        }
    }

    private Connection openConnection() {

        try {
            return dataSource.getConnection();

        } catch (SQLException ex) {
            throw new DatabaseUnavailableException(
                DatabaseEngine.ORACLE,
                ex
            );
        }
    }

    private boolean tableExists(
        Connection connection,
        String tableName
    ) throws SQLException {

        try (
            var statement =
                connection.prepareStatement(
                    TABLE_EXISTS_SQL
                )
        ) {
            statement.setString(
                1,
                tableName
            );

            try (
                var resultSet =
                    statement.executeQuery()
            ) {
                return resultSet.next()
                    && resultSet.getInt(1) > 0;
            }
        }
    }
}