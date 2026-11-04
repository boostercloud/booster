package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
public record ReadModelChange(String readModel, String id, JsonNode value, long position) {}
