import {
  CommandEnvelope,
  InvalidParameterError,
  UUID,
} from '@boostercloud/framework-types'
import { fetchUserFromRequest } from './user-envelopes'
import { UserRegistry } from '../services'

export type CommandResult =
  | { status: 'success'; result: unknown }
  | { status: 'failure'; code: number; title: string; reason: string }

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function rawCommandToEnvelope(userRegistry: UserRegistry, request: any): Promise<CommandEnvelope> {
  if (request.body) {
    const envelope: CommandEnvelope = request.body
    envelope.requestID = UUID.generate().toString()
    envelope.currentUser = await fetchUserFromRequest(request, userRegistry)
    return envelope
  } else {
    throw new InvalidParameterError('The field "body" arrived empty.')
  }
}

