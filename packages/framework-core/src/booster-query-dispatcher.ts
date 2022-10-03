import {
  BoosterConfig,
  QueryEnvelope,
  InvalidParameterError,
  NotFoundError,
  QueryHandlerGlobalError,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { applyQueryBeforeFunctions } from './services/filter-helpers'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'

export class BoosterQueryDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
  }

  public async dispatchQuery(queryEnvelope: QueryEnvelope): Promise<unknown> {
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
      const queryInput = await applyQueryBeforeFunctions(queryEnvelope, queryMetadata.before, queryEnvelope.currentUser)

      logger.debug('Calling "handle" method on query: ', queryClass)
      result = await queryClass.handle(queryInput.filter ?? {})
    } catch (err) {
      const e = err as Error
      const error = await this.globalErrorDispatcher.dispatch(new QueryHandlerGlobalError(queryEnvelope, e))
      if (error) throw error
    }
    logger.debug('Query dispatched with filter: ', queryEnvelope.filter)
    return result
  }
}
