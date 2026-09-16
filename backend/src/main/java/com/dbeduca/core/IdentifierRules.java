package com.dbeduca.core;

import java.util.regex.Pattern;

final class IdentifierRules {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SQL_TYPE = Pattern.compile("[A-Za-z]+(?:\\(\\d+(?:,\\d+)?\\))?");

    private IdentifierRules() {}

    static String requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(label + " inválido: use apenas letras, números e underscore, sem espaços.");
        }
        return value.trim();
    }

    static String requireSqlType(String value) {
        if (value == null || !SQL_TYPE.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Tipo de dado inválido.");
        }
        return value.trim().toUpperCase();
    }
}
