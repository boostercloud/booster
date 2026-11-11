package com.boostercloud.cli.services;

/** The resource templates ({@code TemplateType} of {@code services/stub-publisher.ts}). */
public enum TemplateType {
  /** {@code command.stub}. */
  COMMAND("command"),
  /** {@code query.stub}. */
  QUERY("query"),
  /** {@code entity.stub}. */
  ENTITY("entity"),
  /** {@code event.stub}. */
  EVENT("event"),
  /** {@code event-handler.stub}. */
  EVENT_HANDLER("event-handler"),
  /** {@code read-model.stub}. */
  READ_MODEL("read-model"),
  /** {@code scheduled-command.stub}. */
  SCHEDULED_COMMAND("scheduled-command"),
  /** {@code type.stub}. */
  TYPE("type");

  private final String id;

  TemplateType(String id) {
    this.id = id;
  }

  /** @return the TypeScript name, e.g. {@code read-model} */
  public String id() {
    return id;
  }

  /** @return the stub file name, e.g. {@code read-model.stub} */
  public String fileName() {
    return id + ".stub";
  }
}
