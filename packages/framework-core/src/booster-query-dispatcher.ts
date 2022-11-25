import {
  BoosterConfig,
  InvalidParameterError,
  NotFoundError,
  QueryEnvelope,
  QueryHandlerGlobalError,
} from '@boostercloud/framework-types'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { GraphQLResolverContext } from './services/graphql/common'

export class BoosterQueryDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
  }

  public async dispatchQuery(queryEnvelope: QueryEnvelope, context: GraphQLResolverContext): Promise<unknown> {
    const logger = getLogger(this.config, 'BoosterQueryDispatcher#dispatchQuery')
    logger.debug('Dispatching the following query envelope: ', queryEnvelope)
    if (!queryEnvelope.version) {
      throw new InvalidParameterError('The required query "version" was not present')
    }

    const queryMetadata = this.config.queryHandlers[queryEnvelope.typeName]
    if (!queryMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${queryEnvelope.typeName}`)
    }

    await queryMetadata.authorizer(queryEnvelope.currentUser, queryEnvelope)

    const queryClass = queryMetadata.class
    logger.debug('Found the following query:', queryClass.name)

    let result: unknown
    try {
      const queryInstance = createInstance(queryClass, queryEnvelope.value)
      logger.debug('Calling "handle" method on query: ', queryClass)
      result = await queryClass.handle(queryInstance)
    } catch (err) {
      const e = err as Error
      const error = await this.globalErrorDispatcher.dispatch(new QueryHandlerGlobalError(queryEnvelope, e))
      if (error) throw error
    }
    return result
  }
}
