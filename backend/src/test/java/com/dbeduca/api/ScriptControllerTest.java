package com.dbeduca.api;

import com.dbeduca.core.ScriptGeneratorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ScriptController.class, PlatformController.class, ApiExceptionHandler.class})
class ScriptControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScriptGeneratorRegistry registry;

    @Test
    void listsSupportedEngines() throws Exception {
        mvc.perform(get("/api/v1/platform/engines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("POSTGRESQL"))
            .andExpect(jsonPath("$[3].id").value("ORACLE"));
    }

    @Test
    void generatesScript() throws Exception {
        when(registry.generate(eq(com.dbeduca.core.DatabaseEngine.POSTGRESQL), any()))
            .thenReturn("CREATE TABLE alunos (...);");

        mvc.perform(post("/api/v1/scripts/generate")
                .contentType("application/json")
                .content("""
                    {"engine":"POSTGRESQL","tableName":"alunos","columns":[
                      {"name":"id","type":"BIGINT","primaryKey":true,"notNull":true,"unique":false}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("POSTGRESQL"))
            .andExpect(jsonPath("$.script").exists());
    }
}
