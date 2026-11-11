package com.boostercloud.framework.provider.azure.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Port of {@code AzureHttpResponse} ({@code HttpResponseInit} of {@code @azure/functions} v4): the return value of
 * HTTP-triggered functions. The Java entry points turn it into the worker's {@code HttpResponseMessage}, serialising
 * {@code jsonBody} with {@code BoosterJson}.
 *
 * @param headers the response headers (insertion order kept)
 * @param status the HTTP status
 * @param jsonBody the body to serialise as JSON, or {@code null} for no body
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"headers", "status", "jsonBody"})
public record AzureHttpResponse(Map<String, String> headers, int status, Object jsonBody) {}
