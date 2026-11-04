package io.boostercloud.booster.core;
@FunctionalInterface
public interface QueryHandler<Q> { Object handle(Q query, QueryContext context) throws Exception; }
