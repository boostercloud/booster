package com.boostercloud.cli.services.project;

import java.util.List;

/**
 * The sub-packages of a project's base package where each kind of resource lives. They mirror the TypeScript
 * {@code src/} folders; Java package names cannot contain hyphens, so {@code read-models}, {@code event-handlers}
 * and {@code scheduled-commands} become {@code readmodels}, {@code eventhandlers} and {@code scheduledcommands}.
 */
public final class ResourcePackages {
  /** Commands ({@code src/commands}). */
  public static final String COMMANDS = "commands";
  /** Shared types ({@code src/common}). */
  public static final String COMMON = "common";
  /** Configuration classes ({@code src/config}). */
  public static final String CONFIG = "config";
  /** Entities ({@code src/entities}). */
  public static final String ENTITIES = "entities";
  /** Events ({@code src/events}). */
  public static final String EVENTS = "events";
  /** Event handlers ({@code src/event-handlers}). */
  public static final String EVENT_HANDLERS = "eventhandlers";
  /** Queries ({@code src/queries}). */
  public static final String QUERIES = "queries";
  /** Read models ({@code src/read-models}). */
  public static final String READ_MODELS = "readmodels";
  /** Scheduled commands ({@code src/scheduled-commands}). */
  public static final String SCHEDULED_COMMANDS = "scheduledcommands";

  /** Every resource package, created by {@code new:project}. */
  public static final List<String> ALL =
      List.of(COMMANDS, COMMON, CONFIG, ENTITIES, EVENTS, EVENT_HANDLERS, QUERIES, READ_MODELS, SCHEDULED_COMMANDS);

  private ResourcePackages() {}
}
