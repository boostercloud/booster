package com.boostercloud.framework.provider.local.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** A {@link SchedulerClock} that only moves when a test advances it. */
public final class ManualClock implements SchedulerClock {

  private final ZoneId zone;
  private Instant now;
  private int sleepers;

  public ManualClock(ZonedDateTime start) {
    this.zone = start.getZone();
    this.now = start.toInstant();
  }

  @Override
  public ZoneId zone() {
    return zone;
  }

  @Override
  public synchronized Instant now() {
    return now;
  }

  public synchronized void advance(Duration duration) {
    now = now.plus(duration);
    notifyAll();
  }

  public synchronized void setTo(ZonedDateTime time) {
    now = time.toInstant();
    notifyAll();
  }

  /** @return how many threads are waiting for the clock */
  public synchronized int sleepers() {
    return sleepers;
  }

  @Override
  public synchronized void sleepUntil(Instant deadline) throws InterruptedException {
    sleepers++;
    try {
      while (now.isBefore(deadline)) {
        wait();
      }
    } finally {
      sleepers--;
    }
  }
}
