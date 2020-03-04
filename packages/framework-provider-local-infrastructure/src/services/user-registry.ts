import { UserEnvelope, UUID, BoosterConfig, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { promisify } from 'util'

interface LoginCredentials {
  email: string
  password: string
}

type SignUpUser = UserEnvelope & {
  password: string
  confirmed: boolean
}

interface SignInUser {
  email: string
  token: UUID
}

export class UserRegistry {
  public readonly registeredUsers: DataStore<SignUpUser> = new DataStore()
  public readonly authenticatedUsers: DataStore<SignInUser> = new DataStore()
  constructor(readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async signUp(user: SignUpUser): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    this.registeredUsers.insert(user)
  }

  public async signIn(user: LoginCredentials): Promise<UUID> {
    const token = UUID.generate()
    const registeredMatches = await this.getRegisteredUsersByEmail(user.email)
    if (registeredMatches.length === 0) {
      throw new NotAuthorizedError(`User with email ${user.email} has not been registered `)
    }
    if (!registeredMatches[0].confirmed) {
      throw new NotAuthorizedError(`User with email ${user.email} has not been confirmed`)
    }
    if (registeredMatches[0].password !== user.password) {
      throw new NotAuthorizedError(`Wrong password for user with email ${user.email}`)
    }
    this.authenticatedUsers.insert({ email: user.email, token })
    return token
  }

  public async signOut(token: UUID): Promise<void> {
    this.authenticatedUsers.remove({ token })
  }

  private async getRegisteredUsersByEmail(email: string): Promise<Array<SignUpUser>> {
    return promisify(this.registeredUsers.find({ email }).exec)()
  }
}
