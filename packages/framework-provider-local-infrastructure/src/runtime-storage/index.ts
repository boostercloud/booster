import { UserEnvelope, UUID } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { promisify } from 'util'

type AuthenticatedUser = UserEnvelope & {
  token: UUID
}

/**
 * Class to interact with the local runtime's storage.
 */
export class RuntimeStorage {
  public readonly registeredUsers: DataStore<UserEnvelope> = new DataStore()
  public readonly authenticatedUsers: DataStore<AuthenticatedUser> = new DataStore()
  constructor() {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async registerUser(user: UserEnvelope): Promise<void> {
    this.registeredUsers.insert(user)
  }

  public async authenticateUser(token: UUID, user: UserEnvelope): Promise<void> {
    this.authenticatedUsers.insert({ ...user, token })
  }

  public async getRegisteredUsersByEmail(email: string): Promise<Array<UserEnvelope>> {
    return await promisify(this.registeredUsers.find({ email }).exec)()
  }

  public async signOutUser(token: UUID): Promise<void> {
    this.registeredUsers.remove({ token })
  }
}
