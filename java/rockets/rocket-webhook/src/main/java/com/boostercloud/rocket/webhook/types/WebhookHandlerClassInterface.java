package com.boostercloud.rocket.webhook.types;

/**
 * Port of {@code WebhookHandlerClassInterface} ({@code webhook-handler.ts}): the contract of
 * {@link WebhookParamsEvent#handlerClass()}. As every Booster handler, it is a class with a <b>static</b> method,
 * found by name when a request arrives (the class is never instantiated):
 *
 * <pre>{@code
 * public class TestHandler implements WebhookHandlerClassInterface {    // implementing it is optional
 *   public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
 *     if (validationFails(event)) {
 *       throw new InvalidParameterError("Error message");               // 400 {"title":"Invalid Parameter Error",...}
 *     }
 *     register.events(new SomethingHappened(...));                        // stored after handle returns
 *     return WebhookHandlerReturnType.json(Map.of("name", "my_name"));
 *   }
 * }
 * }</pre>
 *
 * <p>{@code handle} may return {@code void}, a {@link WebhookHandlerReturnType}, or a {@code CompletionStage} of
 * one (it is awaited); like the other Booster handlers it may also declare fewer parameters ({@code handle(event)}).
 */
public interface WebhookHandlerClassInterface {

  /** The name of the static handler method. */
  String HANDLE_METHOD = "handle";
}
