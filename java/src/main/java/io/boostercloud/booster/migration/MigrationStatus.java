package io.boostercloud.booster.migration;

import com.fasterxml.jackson.databind.JsonNode;

public record MigrationStatus(BoosterDataMigrationEntity lifecycle, JsonNode checkpoint, String lastCompletedStep) {
  public MigrationStatus { checkpoint = checkpoint.deepCopy(); }
  @Override public JsonNode checkpoint() { return checkpoint.deepCopy(); }
}
