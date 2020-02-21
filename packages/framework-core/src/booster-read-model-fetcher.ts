import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  ReadModelRequestEnvelope,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { ReadModelInterface } from "@boostercloud/framework-types/dist";

export class BoosterReadModelFetcher {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static async fetch(rawMessage: any, config: BoosterConfig, logger: Logger): Promise<any> {
    try {
      const readModelRequest = await config.provider.rawReadModelRequestToEnvelope(rawMessage)
      this.validateFetchRequest(readModelRequest, config, logger)
      const result = await this.processFetch(readModelRequest, config, logger)
      return config.provider.handleReadModelResult(result)
    } catch (e) {
      return config.provider.handleReadModelError(e)
    }
  }

  private static validateFetchRequest(
    readModelRequest: ReadModelRequestEnvelope,
    config: BoosterConfig,
    logger: Logger
  ): void {
    logger.debug('Validating the following read model request: ', readModelRequest)
    if (!readModelRequest.version) {
      throw new InvalidParameterError('The required request "version" was not present')
    }

    const readModelMetadata = config.readModels[readModelRequest.typeName]
    if (!readModelMetadata) {
      throw new NotFoundError(`Could not find read model ${readModelRequest.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(readModelMetadata.authorizedRoles, readModelRequest.currentUser)) {
      throw new NotAuthorizedError(`Access denied for read model ${readModelRequest.typeName}`)
    }
  }

  private static processFetch(
    readModelRequest: ReadModelRequestEnvelope,
    config: BoosterConfig,
    logger: Logger
  ): Promise<ReadModelInterface | Array<ReadModelInterface>> {
    if (readModelRequest.readModelID) {
      return config.provider.fetchReadModel(config, logger, readModelRequest.typeName, readModelRequest.readModelID)
    }
    return config.provider.fetchAllReadModels(config, logger, readModelRequest.typeName)
  }
}
