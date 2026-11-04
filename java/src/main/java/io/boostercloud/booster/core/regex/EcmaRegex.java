package io.boostercloud.booster.core.regex;

import io.boostercloud.booster.core.BoosterException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** ECMAScript RegExp.test semantics with bounded work, cache size and concurrent executions. */
public final class EcmaRegex {
  public static final int MAX_PATTERN_LENGTH = 512;
  public static final int MAX_INPUT_LENGTH = 1_048_576;
  public static final int MATCH_BUDGET_MILLIS = 250;
  private static final int MAX_WORKERS = 4;
  private static final int CACHE_SIZE = 64;
  private static final Semaphore PERMITS = new Semaphore(MAX_WORKERS);
  private static final ConcurrentLinkedQueue<Worker> IDLE = new ConcurrentLinkedQueue<>();
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final class Runtime {
    // Initialization loads trusted engine classes once; no user pattern participates.
    private static final Engine ENGINE = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build();
  }
  private EcmaRegex() {}

  /** Matches anywhere, exactly like new RegExp(pattern, ignoreCase ? "i" : "").test(input). */
  public static boolean matches(String input, String pattern, boolean ignoreCase) {
    Objects.requireNonNull(input); Objects.requireNonNull(pattern);
    if (pattern.length() > MAX_PATTERN_LENGTH) throw invalid("Regular expression exceeds 512 characters");
    if (input.length() > MAX_INPUT_LENGTH) throw invalid("Regular expression input exceeds 1048576 characters");
    boolean acquired;
    try { acquired = PERMITS.tryAcquire(100, TimeUnit.MILLISECONDS); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw invalid("Regular expression interrupted"); }
    if (!acquired) throw invalid("Regular expression execution capacity reached");
    Worker worker = null;
    boolean reusable = false;
    try {
      worker = IDLE.poll();
      if (worker == null) worker = new Worker();
      var selected = worker;
      var result = worker.executor.submit(() -> selected.match(input, pattern, ignoreCase));
      try {
        boolean value = result.get(MATCH_BUDGET_MILLIS, TimeUnit.MILLISECONDS);
        reusable = true; return value;
      } catch (TimeoutException timeout) {
        // Context cancellation reaches TRegex backtracking-loop safepoints. Interrupting
        // a java.util.regex thread alone would not stop its matching work.
        throw invalid("Regular expression exceeded its 250 ms execution budget");
      } catch (ExecutionException failure) {
        if (failure.getCause() instanceof PolyglotException polyglot && polyglot.isSyntaxError()) {
          reusable = true; throw invalid("Invalid ECMAScript regular expression");
        }
        if (failure.getCause() instanceof BoosterException known) throw known;
        throw new BoosterException(BoosterException.Code.INTERNAL, "Regular expression execution failed", failure.getCause());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt(); throw invalid("Regular expression interrupted");
      }
    } finally {
      try {
        if (worker != null) {
          if (reusable) IDLE.offer(worker);
          else worker.close();
        }
      } finally { PERMITS.release(); }
    }
  }
  private record Key(String pattern, boolean ignoreCase) {}
  private static final class Worker implements AutoCloseable {
    final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon()
        .name("booster-regex-" + SEQUENCE.incrementAndGet()).factory());
    final Context context;
    final LinkedHashMap<Key, Value> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<Key, Value> eldest) { return size() > CACHE_SIZE; }
    };
    Worker() {
      context = Context.newBuilder(RegexLanguage.ID).engine(Runtime.ENGINE).allowAllAccess(false).build();
      var initialization = executor.submit(() -> match("", "", false));
      try { initialization.get(3, TimeUnit.SECONDS); }
      catch (Exception failure) {
        close();
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new BoosterException(BoosterException.Code.INTERNAL, "Regular expression engine initialization failed", failure);
      }
    }
    boolean match(String input, String pattern, boolean ignoreCase) {
      var key = new Key(pattern, ignoreCase);
      Value compiled = cache.computeIfAbsent(key, unused -> {
        // TRegex finds the first and LAST delimiter. Slashes inside the pattern are
        // preserved, and the caller cannot inject engine options or additional flags.
        String source = "Flavor=ECMAScript/" + pattern + "/" + (ignoreCase ? "i" : "");
        Value value = context.eval(Source.newBuilder(RegexLanguage.ID, source, "read-model-filter").cached(false).buildLiteral());
        if (value.isNull()) throw invalid("ECMAScript regular expression is unsupported by the engine");
        return value;
      });
      return compiled.invokeMember("exec", input, 0).getMember("isMatch").asBoolean();
    }
    @Override public void close() {
      try { context.close(true); }
      finally { cache.clear(); executor.shutdownNow(); }
    }
  }
  private static BoosterException invalid(String message) { return new BoosterException(BoosterException.Code.VALIDATION, message); }
}
