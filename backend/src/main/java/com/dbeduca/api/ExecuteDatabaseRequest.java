package com.dbeduca.api;

import com.dbeduca.core.ColumnDefinition;
import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.core.TableDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExecuteDatabaseRequest(
    @NotNull DatabaseEngine engine,
    @NotBlank String tableName,
    @NotEmpty List<@Valid ColumnRequest> columns
) {

    public TableDefinition toDomain() {
        return new TableDefinition(
            tableName,
            columns.stream()
                .map(ColumnRequest::toDomain)
                .toList()
        );
    }

    public record ColumnRequest(
        @NotBlank String name,
        @NotBlank String type,
        boolean primaryKey,
        boolean notNull,
        boolean unique
    ) {
        ColumnDefinition toDomain() {
            return new ColumnDefinition(
                name,
                type,
                primaryKey,
                notNull,
                unique
            );
        }
    }
}