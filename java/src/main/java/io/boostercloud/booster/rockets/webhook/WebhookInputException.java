package io.boostercloud.booster.rockets.webhook;

final class WebhookInputException extends RuntimeException {
  final int status;
  WebhookInputException(int status, String message) { super(message); this.status = status; }
}
