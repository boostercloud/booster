package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.services.FilterHelpers;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.QueryInterface;
import com.boostercloud.framework.types.concepts.QueryMetadata;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.errors.QueryHandlerGlobalError;
import java.util.Map;

/**
 * Port of {@code booster-query-dispatcher.ts}: runs one query (not traced, no schema migration, no register).
 *
 * <ol>
 *   <li>{@code version} must be set: {@link InvalidParameterError} {@code The required query "version" was not
 *       present};</li>
 *   <li>the query must be registered: {@link NotFoundError} {@code Could not find a proper handler for <type>};</li>
 *   <li>the authorizer runs;</li>
 *   <li>inside a {@code try}: the {@link QueryInfo} is built (sharing the context's response headers), the
 *       {@code before} hooks run on the raw input, the instance is created and the static
 *       {@code handle(query, queryInfo)} runs; failures go to the global error handler;</li>
 *   <li>the result is returned ({@code null} when the error was swallowed).</li>
 * </ol>
 */
public class BoosterQueryDispatcher {

  private final BoosterConfig config;
  private final BoosterGlobalErrorDispatcher globalErrorDispatcher;

  /**
   * @param config the configuration (the global error handler is captured now)
   */
  public BoosterQueryDispatcher(BoosterConfig config) {
    this.config = config;
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config);
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatchQuery(queryEnvelope, context)}.
   *
   * @param queryEnvelope the query
   * @param context the GraphQL resolver context
   * @return the handler's result
   */
  public Object dispatchQuery(QueryEnvelope queryEnvelope, GraphQLResolverContext context) {
    Logger logger = Loggers.getLogger(config, "BoosterQueryDispatcher#dispatchQuery");
    logger.debug("Dispatching the following query envelope: ", queryEnvelope);
    if (queryEnvelope.version() == 0) {
      throw new InvalidParameterError("The required query \"version\" was not present");
    }
    QueryMetadata queryMetadata = config.queryHandlers.get(queryEnvelope.typeName());
    if (queryMetadata == null) {
      throw new NotFoundError("Could not find a proper handler for " + queryEnvelope.typeName());
    }
    queryMetadata.authorizer().authorize(queryEnvelope.currentUser(), queryEnvelope);

    Class<?> queryClass = queryMetadata.type();
    logger.debug("Found the following query:", queryClass.getSimpleName());
    Object result = null;
    try {
      QueryInfo queryInfo = new QueryInfo(queryEnvelope.requestID(), BoosterCommandDispatcher.responseHeaders(context),
          queryEnvelope.currentUser(), queryEnvelope.context());
      Map<String, Object> queryInput = FilterHelpers.applyQueryBeforeFunctions(queryEnvelope.value(), queryMetadata.before(),
          queryEnvelope.currentUser());
      Object queryInstance = Instances.createInstance(queryClass, queryInput);
      logger.debug("Calling \"handle\" method on query: ", queryClass);
      result = UserCode.invokeStatic(queryClass, QueryInterface.HANDLE_METHOD, queryInstance, queryInfo);
    } catch (Exception e) {
      Throwable error = globalErrorDispatcher.dispatch(new QueryHandlerGlobalError(queryEnvelope, e));
      if (error != null) {
        throw UserCode.rethrow(error);
      }
    }
    return result;
  }
}
