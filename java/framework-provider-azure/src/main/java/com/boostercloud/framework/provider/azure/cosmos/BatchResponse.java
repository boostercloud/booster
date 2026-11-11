package com.boostercloud.framework.provider.azure.cosmos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * The response of a transactional batch ({@code Response<OperationResponse[]>} of {@code @azure/cosmos}): the
 * batch succeeded when {@code code} is 200; a failed (rolled back) batch has another code (207 in practice) and
 * per-operation statuses in {@code result}.
 *
 * @param headers the response headers
 * @param result one entry per operation
 * @param code the HTTP status code
 * @param substatus the {@code x-ms-substatus} header, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"headers", "result", "code", "substatus"})
public record BatchResponse(Map<String, String> headers, List<OperationResponse> result, int code, Integer substatus) {

  /**
   * The outcome of one operation.
   *
   * @param statusCode its status code (201, 409, 424 for "failed dependency"...)
   * @param requestCharge request units consumed
   * @param eTag the new etag, optional
   * @param resourceBody the resulting document, optional
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonPropertyOrder({"statusCode", "requestCharge", "eTag", "resourceBody"})
  public record OperationResponse(
      int statusCode, Double requestCharge, String eTag, Map<String, Object> resourceBody) {}
}
