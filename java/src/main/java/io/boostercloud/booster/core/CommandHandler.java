package io.boostercloud.booster.core;
@FunctionalInterface
public interface CommandHandler<C> { Object handle(C command, Register register) throws Exception; }
