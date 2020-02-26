import { UserEnvelope } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'

/**
 * Class to interact with the local runtime's storage.
 */
export class RuntimeStorage {
  public readonly registeredUsers: DataStore<UserEnvelope> = new DataStore()
  constructor() {
    this.registeredUsers.loadDatabase()
  }

  public async registerUser(user: UserEnvelope): Promise<void> {
    this.registeredUsers.insert(user)
  }
}
