import { UserEnvelope, UUID, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'

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
    this.registeredUsers.find({ email: user.email }, (err, docs) => {
      if (docs.length < 1) throw new NotAuthorizedError(`User with email ${user.email} has not been registered `)
      this.authenticatedUsers.insert({ ...user, token })
    })
  }
}
