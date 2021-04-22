/* eslint-disable @typescript-eslint/no-unused-vars */
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'

export const rawToEnvelope = (_rawMessage: unknown): UserEnvelope => ({ role: '', username: '' })

export const fromAuthToken = async (_token: string): Promise<UserEnvelope | undefined> => {
  throw new Error('authAdapter#fromAuthToken: Not implemented')
}

export const handleSignUpResult = (_config: BoosterConfig, _request: unknown, _userEnvelope: UserEnvelope): unknown => {
  throw new Error('authAdapter#fromAuthToken: Not implemented')
}
