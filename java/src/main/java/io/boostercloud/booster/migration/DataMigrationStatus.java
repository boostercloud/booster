package io.boostercloud.booster.migration;

/** RUNNING and FINISHED preserve the Booster lifecycle; FAILED is explicitly resumable. */
public enum DataMigrationStatus { RUNNING, FINISHED, FAILED }
