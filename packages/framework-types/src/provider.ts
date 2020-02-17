/* eslint-disable @typescript-eslint/no-explicit-any */
import { CommandEnvelope, EventEnvelope, UserEnvelope } from './envelope'
import { BoosterConfig } from './config'
import { Observable } from 'rxjs'
import { Logger } from './logger'
import { ReadModelInterface, UUID } from './concepts'

export type ProviderLibrary = ProviderCommandsLibrary &
  ProviderEventsLibrary &
  ProviderReadModelsLibrary &
  ProviderAuthLibrary & {
    getInfrastructure(): ProviderInfrastructure
  }

export interface ProviderCommandsLibrary {
  rawCommandToEnvelope(rawCommand: any): Promise<CommandEnvelope>
  handleCommandResult(config: BoosterConfig, events: Array<EventEnvelope>, logger?: Logger): Promise<any>
  handleCommandError(config: BoosterConfig, error: Error, logger?: Logger): Promise<any>
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
  processReadModelAPICall(config: BoosterConfig, message: any): Promise<any>
  fetchReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModelID: UUID
  ): Promise<ReadModelInterface>
  storeReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModel: ReadModelInterface
  ): Promise<any>
}

export interface ProviderAuthLibrary {
  rawSignUpDataToUserEnvelope(rawMessage: any): UserEnvelope
}

export interface ProviderInfrastructure {
  deploy(configuration: BoosterConfig): Observable<string>
  nuke(configuration: BoosterConfig): Observable<string>
}
