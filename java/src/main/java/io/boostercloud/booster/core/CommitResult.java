package io.boostercloud.booster.core;

public record CommitResult(boolean duplicate, CommandReceipt receipt) {}
