package com.boostercloud.framework.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Reflective calls of user code ({@link UserCode}). */
class UserCodeTest {

  public static class Base {
    public static String inherited(String value) {
      return "inherited " + value;
    }
  }

  public static class Handlers extends Base {
    private int counter;

    public static String handle(String command) {
      return "one " + command;
    }

    public static String overloaded(Object value) {
      return "object";
    }

    public static String overloaded(String value) {
      return "string";
    }

    public static int primitive(int value) {
      return value + 1;
    }

    public static CompletableFuture<String> async(String value) {
      return CompletableFuture.supplyAsync(() -> "async " + value);
    }

    public static void checked() throws IOException {
      throw new IOException("checked failure");
    }

    public static CompletableFuture<String> failedAsync() {
      return CompletableFuture.failedFuture(new IllegalArgumentException("async failure"));
    }

    public int increment() {
      return ++counter;
    }
  }

  static class NoDefaultConstructor {
    NoDefaultConstructor(String value) {}
  }

  @Test
  void callsMethodsDeclaringFewerParametersThanTheArguments() {
    assertThat(UserCode.invokeStatic(Handlers.class, "handle", "x", "ignored register")).isEqualTo("one x");
  }

  @Test
  void choosesTheMostSpecificOverloadAndInheritedStaticMethods() {
    assertThat(UserCode.invokeStatic(Handlers.class, "overloaded", "x")).isEqualTo("string");
    assertThat(UserCode.invokeStatic(Handlers.class, "overloaded", 1)).isEqualTo("object");
    assertThat(UserCode.invokeStatic(Handlers.class, "inherited", "x")).isEqualTo("inherited x");
    assertThat(UserCode.invokeStatic(Handlers.class, "primitive", 41)).isEqualTo(42);
  }

  @Test
  void reportsMissingMethods() {
    assertThatThrownBy(() -> UserCode.invokeStatic(Handlers.class, "nope", "x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Handlers.nope is not a function");
    assertThatThrownBy(() -> UserCode.invokeStatic(Handlers.class, "primitive", (Object) null))
        .hasMessage("No static method Handlers.primitive accepts the arguments [null]");
    assertThat(UserCode.findStatic(Handlers.class, "increment")).isEmpty();
    assertThat(UserCode.findInstance(Handlers.class, "increment")).isPresent();
  }

  @Test
  void waitsForAsynchronousResultsAndPropagatesErrorsUnchanged() {
    assertThat(UserCode.invokeStatic(Handlers.class, "async", "x")).isEqualTo("async x");
    assertThatThrownBy(() -> UserCode.invokeStatic(Handlers.class, "checked"))
        .isInstanceOf(IOException.class).hasMessage("checked failure");
    assertThatThrownBy(() -> UserCode.invokeStatic(Handlers.class, "failedAsync"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("async failure");
  }

  @Test
  void createsInstancesWithTheNoArgumentConstructor() {
    Handlers handlers = UserCode.newInstance(Handlers.class);

    assertThat(UserCode.invoke(UserCode.findInstance(Handlers.class, "increment").orElseThrow(), handlers)).isEqualTo(1);
    assertThatThrownBy(() -> UserCode.newInstance(NoDefaultConstructor.class))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs a constructor without parameters");
  }
}
