package com.dbeduca.config;

import com.dbeduca.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ApplicationConfig {
    @Bean
    ScriptGeneratorRegistry scriptGeneratorRegistry() {
        return new ScriptGeneratorRegistry(List.of(
            new PostgresScriptGenerator(),
            new MySqlScriptGenerator(),
            new MongoScriptGenerator()
        ));
    }
}
