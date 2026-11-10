package com.boostercloud.framework.provider.azure.cosmos;

import com.boostercloud.framework.types.provider.AzureConfiguration;

/**
 * Request options ({@code RequestOptions} of {@code @azure/cosmos}): the subset Booster uses.
 *
 * @param accessCondition an optimistic concurrency condition, or {@code null}
 * @param consistencyLevel {@code Strong}, {@code BoundedStaleness}, {@code Session}, {@code Eventual},
 *     {@code ConsistentPrefix}, or {@code null}
 * @param disableRUPerMinuteUsage optional
 * @param indexingDirective {@code Include} / {@code Exclude}, or {@code null}
 */
public record RequestOptions(
    AccessCondition accessCondition,
    String consistencyLevel,
    Boolean disableRUPerMinuteUsage,
    String indexingDirective) {

  /**
   * An access condition ({@code { condition, type }}).
   *
   * @param type {@code IfMatch} or {@code IfNoneMatch}
   * @param condition the etag, or {@code *}
   */
  public record AccessCondition(String type, String condition) {

    /**
     * @param condition the etag (or {@code *})
     * @return an {@code IfMatch} condition
     */
    public static AccessCondition ifMatch(String condition) {
      return new AccessCondition("IfMatch", condition);
    }
  }

  /**
   * @return no options ({@code {}})
   */
  public static RequestOptions none() {
    return new RequestOptions(null, null, null, null);
  }

  /**
   * @param condition the access condition
   * @return options carrying only the access condition
   */
  public static RequestOptions withAccessCondition(AccessCondition condition) {
    return new RequestOptions(condition, null, null, null);
  }

  /**
   * {@code { ...config.azureConfiguration?.cosmos?.requestOptions }}.
   *
   * @param options the configured options, or {@code null}
   * @return the equivalent request options ({@link #none()} when {@code null})
   */
  public static RequestOptions from(AzureConfiguration.RequestOptions options) {
    if (options == null) {
      return none();
    }
    return new RequestOptions(
        null, options.consistencyLevel(), options.disableRUPerMinuteUsage(), options.indexingDirective());
  }
}
