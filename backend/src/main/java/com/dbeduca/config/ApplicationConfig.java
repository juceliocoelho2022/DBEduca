package com.dbeduca.config;

import com.dbeduca.core.*;
import com.dbeduca.execution.DatabaseExecutionAdapter;
import com.dbeduca.execution.DatabaseExecutionService;
import com.dbeduca.execution.OracleExecutionAdapter;
import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

@Configuration
public class ApplicationConfig {

    @Bean
    ScriptGeneratorRegistry scriptGeneratorRegistry() {
        return new ScriptGeneratorRegistry(List.of(
            new PostgresScriptGenerator(),
            new MySqlScriptGenerator(),
            new MongoScriptGenerator(),
            new OracleScriptGenerator()
        ));
    }

    @Bean
    DataSource oracleDataSource(
        @Value("${dbeduca.oracle.jdbc-url}") String jdbcUrl,
        @Value("${dbeduca.oracle.username}") String username,
        @Value("${dbeduca.oracle.password}") String password
    ) throws SQLException {

        OracleDataSource dataSource =
            new OracleDataSource();

        dataSource.setURL(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);

        return dataSource;
    }

    @Bean
    DatabaseExecutionAdapter oracleExecutionAdapter(
        DataSource oracleDataSource
    ) {
        return new OracleExecutionAdapter(
            oracleDataSource
        );
    }

    @Bean
    DatabaseExecutionService databaseExecutionService(
        ScriptGeneratorRegistry scriptGeneratorRegistry,
        List<DatabaseExecutionAdapter> adapters
    ) {
        return new DatabaseExecutionService(
            scriptGeneratorRegistry,
            adapters
        );
    }
}