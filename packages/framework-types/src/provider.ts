/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  CommandEnvelope,
  EventEnvelope,
  GraphQLRequestEnvelope,
  ReadModelRequestEnvelope,
  UserEnvelope,
} from './envelope'
import { BoosterConfig } from './config'
import { Observable } from 'rxjs'
import { Logger } from './logger'
import { ReadModelInterface, UUID } from './concepts'
import { Filter } from './searcher'

export type ProviderLibrary = ProviderCommandsLibrary &
  ProviderEventsLibrary &
  ProviderReadModelsLibrary &
  ProviderAuthLibrary &
  ProviderGraphQLLibrary & {
    getInfrastructure(): ProviderInfrastructure
  } & ProviderSearcher

export interface ProviderCommandsLibrary {
  rawCommandToEnvelope(rawCommand: any): Promise<CommandEnvelope>
  handleCommandResult(config: BoosterConfig, events: Array<EventEnvelope>, logger?: Logger): Promise<any>
  handleCommandError(error: Error): Promise<any>
}

export interface ProviderEventsLibrary {
  rawEventsToEnvelopes(rawEvents: any): Array<EventEnvelope>
  storeEvent(config: BoosterConfig, logger: Logger, envelope: EventEnvelope): Promise<any>
  readEntityEventsSince(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID,
    since?: string
  ): Promise<Array<EventEnvelope>>
  readEntityLatestSnapshot(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID
  ): Promise<EventEnvelope | null>
}
export interface ProviderReadModelsLibrary {
  rawReadModelRequestToEnvelope(rawReadModelRequest: any): Promise<ReadModelRequestEnvelope>
  fetchReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModelID: UUID
  ): Promise<ReadModelInterface>
  fetchAllReadModels(config: BoosterConfig, logger: Logger, readModelName: string): Promise<Array<ReadModelInterface>>
  storeReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModel: ReadModelInterface
  ): Promise<any>
  handleReadModelResult(readModels: ReadModelInterface | Array<ReadModelInterface>): Promise<any>
  handleReadModelError(error: Error): Promise<any>
}

export interface ProviderGraphQLLibrary {
  authorizeRequest(rawRequest: any, logger: Logger): Promise<any>
  rawGraphQLRequestToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<GraphQLRequestEnvelope>
  handleGraphQLResult(result?: any): Promise<any>
  handleGraphQLError(error: Error): Promise<any>
}

export interface ProviderAuthLibrary {
  rawSignUpDataToUserEnvelope(rawMessage: any): UserEnvelope
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig) => Observable<string>
  run?: (configuration: BoosterConfig, port: number) => Promise<void>
  nuke(configuration: BoosterConfig): Observable<string>
}

export interface ProviderSearcher {
  searchReadModel<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: Record<string, Filter<any>>
  ): Promise<Array<TReadModel>>
}
