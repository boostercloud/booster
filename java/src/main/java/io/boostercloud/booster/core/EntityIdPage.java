package io.boostercloud.booster.core;
import java.util.List;
public record EntityIdPage(List<String> items, long count, String cursor) {
  public EntityIdPage { items = List.copyOf(items); }
}
