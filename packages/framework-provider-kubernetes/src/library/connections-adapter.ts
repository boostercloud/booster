/* eslint-disable @typescript-eslint/no-unused-vars */
import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'

export const storeData = (
  _config: BoosterConfig,
  _connectionID: string,
  _data: ConnectionDataEnvelope
): Promise<void> => {
  throw new Error('connectionsAdapter#storeData: Not implemented')
}

export const fetchData = (
  _config: BoosterConfig,
  _connectionID: string
): Promise<ConnectionDataEnvelope | undefined> => {
  throw new Error('connectionsAdapter#storeData: Not implemented')
}

export const deleteData = (_config: BoosterConfig, _connectionID: string): Promise<void> => {
  throw new Error('connectionsAdapter#storeData: Not implemented')
}

export const sendMessage = (_config: BoosterConfig, _connectionID: string, _data: unknown): Promise<void> => {
  throw new Error('connectionsAdapter#storeData: Not implemented')
}
