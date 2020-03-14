import { UUID, BoosterConfig, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { RegisteredUser, AuthenticatedUser, SignUpUser, LoginCredentials } from '@boostercloud/framework-provider-local'
import { registeredUsersDatabase, authenticatedUsersDatabase } from '@boostercloud/framework-provider-local'

export class UserRegistry {
  public readonly registeredUsers: DataStore<RegisteredUser> = new DataStore(registeredUsersDatabase)
  public readonly authenticatedUsers: DataStore<AuthenticatedUser> = new DataStore(authenticatedUsersDatabase)
  constructor(readonly port: number, readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async signUp(user: SignUpUser): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    const matches = await this.getRegisteredUsersByEmail(user.username)
    if (matches.length !== 0) throw new NotAuthorizedError(`User with username ${user.username} is already registered`)
    this.registeredUsers.insert({ ...user, confirmed: false })
    console.info(
      `To confirm the user, use the following link: http://localhost:${this.port}/auth/confirm/${user.username}`
    )
  }

  public async signIn(user: LoginCredentials): Promise<UUID> {
    const registeredMatches = await this.getRegisteredUsersByEmail(user.username)
    const match = registeredMatches?.[0]
    if (!match || !this.passwordsMatch(user, match)) {
      throw new NotAuthorizedError('Incorrect username or password')
    }
    if (!match.confirmed) {
      throw new NotAuthorizedError(`User with username ${user.username} has not been confirmed`)
    }
    const token = UUID.generate()
    this.authenticatedUsers.insert({ username: user.username, token })
    return token
  }

  public async signOut(token: UUID): Promise<void> {
    return new Promise((resolve, reject) => {
      this.authenticatedUsers.remove({ token }, { multi: true }, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async confirmUser(username: string): Promise<void> {
    return new Promise((resolve, reject) => {
      this.registeredUsers.update({ username }, { $set: { confirmed: true } }, {}, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  private async getRegisteredUsersByEmail(username: string): Promise<Array<RegisteredUser>> {
    return new Promise((resolve, reject) => {
      this.registeredUsers.find({ username }, (err, docs) => {
        err ? reject(err) : resolve(docs)
      })
    })
  }

  private passwordsMatch(credentials: LoginCredentials, registered: RegisteredUser): boolean {
    return registered.password === credentials.password
  }
}
