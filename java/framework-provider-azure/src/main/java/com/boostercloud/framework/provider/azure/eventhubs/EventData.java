package com.boostercloud.framework.provider.azure.eventhubs;

/**
 * An event ({@code EventData} of {@code @azure/event-hubs}). Like the JavaScript SDK, a body that is not a string is
 * sent as its JSON serialization.
 *
 * @param body the body (a raw value, a record, or a string)
 */
public record EventData(Object body) {}
