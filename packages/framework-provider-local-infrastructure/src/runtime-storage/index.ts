import { UserEnvelope } from '@boostercloud/framework-types'

type Email = string

/**
 * Class to interact with the local runtime's storage.
 */
export class RuntimeStorage {
  public readonly registeredUsers: Record<Email, UserEnvelope> = {}
  constructor() {}
}
