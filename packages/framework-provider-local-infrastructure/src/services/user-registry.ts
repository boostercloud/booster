import { UserEnvelope, UUID, BoosterConfig, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { promisify } from 'util'

type AuthenticatedUser = UserEnvelope & {
  token: UUID
}

export class UserRegistry {
  public readonly registeredUsers: DataStore<UserEnvelope> = new DataStore()
  public readonly authenticatedUsers: DataStore<AuthenticatedUser> = new DataStore()
  constructor(readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async signUp(user: UserEnvelope): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    this.registeredUsers.insert(user)
  }

  public async signIn(user: UserEnvelope): Promise<UUID> {
    await this.userProject.boosterPreSignUpChecker(user)
    const token = UUID.generate()
    const registeredMatches = await this.getRegisteredUsersByEmail(user.email)
    if (registeredMatches.length === 0) {
      throw new NotAuthorizedError(`User with email ${user.email} has not been registered `)
    }
    this.authenticatedUsers.insert({ ...user, token })
    return token
  }

  public async signOut(token: UUID): Promise<void> {
    this.registeredUsers.remove({ token })
  }

  private async getRegisteredUsersByEmail(email: string): Promise<Array<UserEnvelope>> {
    return await promisify(this.registeredUsers.find({ email }).exec)()
  }
}
