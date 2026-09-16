package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    @GetMapping("/engines")
    public List<EngineInfo> engines() {
        return List.of(
            new EngineInfo(DatabaseEngine.POSTGRESQL.name(), "PostgreSQL", "SQL relacional"),
            new EngineInfo(DatabaseEngine.MYSQL.name(), "MySQL", "SQL relacional"),
            new EngineInfo(DatabaseEngine.MONGODB.name(), "MongoDB", "NoSQL documental"),
            new EngineInfo(DatabaseEngine.ORACLE.name(), "Oracle", "SQL relacional")
        );
    }

    public record EngineInfo(String id, String name, String category) {}
}
