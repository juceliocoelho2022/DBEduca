package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;

public record GenerateScriptResponse(DatabaseEngine engine, String script) {}
