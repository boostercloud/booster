package com.boostercloud.framework.provider.local.store;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Generates NeDB-style {@code _id}s: 16 characters from {@code [0-9A-Za-z]} (NeDB's {@code uid(16)}).
 *
 * <p>Unlike NeDB's purely random ids, the first 10 characters encode a process-wide monotonic counter derived from the
 * clock (milliseconds × 4096, base 62 with an alphabet in code-unit order) and the last 6 are random. NeDB returns
 * unsorted query results and sort ties in ascending {@code _id} order, so with these ids that order is the insertion
 * order for documents written by this store (TypeScript's order is random). Ids written by NeDB keep their arbitrary
 * place in the order.
 */
final class IdGenerator {

  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final int COUNTER_LENGTH = 10;
  private static final int RANDOM_LENGTH = 6;
  private static final AtomicLong LAST = new AtomicLong();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final LongSupplier clock;

  IdGenerator(LongSupplier clock) {
    this.clock = clock;
  }

  String next() {
    long now = clock.getAsLong() * 4096L;
    long counter = LAST.updateAndGet(previous -> Math.max(previous + 1, now));
    char[] id = new char[COUNTER_LENGTH + RANDOM_LENGTH];
    long value = counter;
    for (int i = COUNTER_LENGTH - 1; i >= 0; i--) {
      id[i] = ALPHABET[(int) Math.floorMod(value, 62L)];
      value = Math.floorDiv(value, 62L);
    }
    for (int i = COUNTER_LENGTH; i < id.length; i++) {
      id[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
    }
    return new String(id);
  }
}
