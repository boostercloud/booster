package com.boostercloud.framework.core.instrumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterCommandDispatcher;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.instrumentation.TraceConfiguration;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/instrumentation/decorators/trace.test.ts}, plus the semantics of spec 05 §3.9. */
class TraceTest extends BoosterTestSupport {

  static final List<String> EVENTS = new ArrayList<>();
  static final List<TraceInfo> INFOS = new ArrayList<>();

  public static class TestClass {
    public String innerField;

    @Trace(value = "CUSTOM_ACTION", description = "my description")
    public String myCustomMethod(String param) {
      EVENTS.add("method");
      innerField = param;
      return "result " + param;
    }

    @Trace
    public static String failing(String param) {
      EVENTS.add("method");
      throw new IllegalStateException("failed " + param);
    }
  }

  private BoosterConfig config;
  private Method custom;
  private Method failing;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    EVENTS.clear();
    INFOS.clear();
    config = Booster.config();
    config.traceConfiguration.enableAll();
    config.traceConfiguration.onStart = (configuration, actionType, info) -> {
      EVENTS.add("onStart " + actionType);
      INFOS.add(info);
    };
    config.traceConfiguration.onEnd = (configuration, actionType, info) -> {
      EVENTS.add("onEnd " + actionType + (info.elapsedInvocationMillis == null ? "" : " timed"));
      INFOS.add(info);
    };
    custom = TestClass.class.getMethod("myCustomMethod", String.class);
    failing = TestClass.class.getMethod("failing", String.class);
  }

  @Test
  void keepsThisAndNotifiesStartAndEndAroundTheMethod() {
    TestClass instance = new TestClass();

    Object result = UserCode.invoke(custom, instance, "test");

    assertThat(result).isEqualTo("result test");
    assertThat(instance.innerField).isEqualTo("test");
    assertThat(EVENTS).containsExactly("onStart CUSTOM_ACTION", "method", "onEnd CUSTOM_ACTION timed");
    assertThat(INFOS.get(0)).isSameAs(INFOS.get(1));
    TraceInfo info = INFOS.getFirst();
    assertThat(info.className).isEqualTo("TestClass");
    assertThat(info.methodName).isEqualTo("myCustomMethod");
    assertThat(info.args).containsExactly("test");
    assertThat(info.description).isEqualTo("my description");
    assertThat(info.traceId).isNotNull();
    assertThat(info.internal).isNull();
    assertThat(info.elapsedInvocationMillis).isGreaterThanOrEqualTo(0);
  }

  @Test
  void addsTheInternalInformationWhenAsked() {
    config.traceConfiguration.includeInternal = true;
    TestClass instance = new TestClass();

    UserCode.invoke(custom, instance, "x");

    assertThat(INFOS.getFirst().internal).isEqualTo(new TraceInfo.Internal(instance, custom));
  }

  @Test
  void notifiesTheEndWhenTheMethodFailsAndUsesTheClassForStaticMethods() {
    config.traceConfiguration.includeInternal = true;

    assertThatThrownBy(() -> UserCode.invoke(failing, null, "x")).hasMessage("failed x");

    assertThat(EVENTS).containsExactly("onStart CUSTOM", "method", "onEnd CUSTOM timed");
    assertThat(INFOS.getFirst().internal.target()).isEqualTo(TestClass.class);
    assertThat(INFOS.getFirst().description).isNull();
  }

  @Test
  void doesNotRunTheMethodWhenOnStartFails() {
    config.traceConfiguration.onStart = (configuration, actionType, info) -> {
      throw new IllegalStateException("onStart failed");
    };

    assertThatThrownBy(() -> UserCode.invoke(custom, new TestClass(), "x")).hasMessage("onStart failed");
    assertThat(EVENTS).isEmpty();
  }

  @Test
  void anOnEndFailureReplacesTheResult() {
    config.traceConfiguration.onEnd = (configuration, actionType, info) -> {
      throw new IllegalStateException("onEnd failed");
    };

    assertThatThrownBy(() -> UserCode.invoke(custom, new TestClass(), "x")).hasMessage("onEnd failed");
    assertThat(EVENTS).containsExactly("onStart CUSTOM_ACTION", "method");
  }

  @Test
  void tracesOnlyTheEnabledActions() {
    config.traceConfiguration.enableFor("OTHER");
    UserCode.invoke(custom, new TestClass(), "x");
    config.traceConfiguration.disable();
    UserCode.invoke(custom, new TestClass(), "x");
    config.traceConfiguration = null;
    UserCode.invoke(custom, new TestClass(), "x");

    assertThat(EVENTS).containsExactly("method", "method", "method");
  }

  @Test
  void isTraceEnabledFollowsTheTypeScriptRules() {
    BoosterConfig other = new BoosterConfig("test");

    assertThat(TraceNotifier.isTraceEnabled("A", null)).isFalse();
    assertThat(TraceNotifier.isTraceEnabled("A", other)).isFalse();
    other.traceConfiguration = new TraceConfiguration().enableFor("A", "B");
    assertThat(TraceNotifier.isTraceEnabled("A", other)).isTrue();
    assertThat(TraceNotifier.isTraceEnabled("C", other)).isFalse();
    other.traceConfiguration.enableTraceNotification = new String[] {"C"};
    assertThat(TraceNotifier.isTraceEnabled("C", other)).isTrue();
    other.traceConfiguration.enableTraceNotification = List.of();
    assertThat(TraceNotifier.isTraceEnabled("C", other)).isFalse();
    other.traceConfiguration.enableAll();
    assertThat(TraceNotifier.isTraceEnabled("anything", other)).isTrue();
    other.traceConfiguration = null;
    assertThat(TraceNotifier.isTraceEnabled("A", other)).isFalse();
  }

  @Test
  void theFrameworkTracePointsUseTheGlobalConfiguration() {
    CommandEnvelope envelope = CommandEnvelope.builder().typeName("X").version(0).build();

    assertThatThrownBy(() -> new BoosterCommandDispatcher(new BoosterConfig("other")).dispatchCommand(envelope, null))
        .isInstanceOf(InvalidParameterError.class);

    assertThat(EVENTS).containsExactly("onStart COMMAND_HANDLER", "onEnd COMMAND_HANDLER timed");
    assertThat(INFOS.getFirst().className).isEqualTo("BoosterCommandDispatcher");
    assertThat(INFOS.getFirst().methodName).isEqualTo("dispatchCommand");
    assertThat(INFOS.getFirst().args).containsExactly(envelope, null);
  }
}
