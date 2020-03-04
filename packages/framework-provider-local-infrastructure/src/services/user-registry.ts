import { UUID, BoosterConfig, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { promisify } from 'util'

interface User {
  clientId: string
  username: string
  password: string
  userAttributes: {
    roles: Array<string>
  }
  token: UUID
  confirmed: boolean
}

type LoginCredentials = Pick<User, 'username' | 'password'>
type SignUpUser = Pick<User, 'clientId' | 'username' | 'password' | 'userAttributes'>
type RegisteredUser = Pick<User, 'username' | 'password' | 'userAttributes' | 'confirmed'>
type AuthenticatedUser = Pick<User, 'username' | 'token'>

export class UserRegistry {
  public readonly registeredUsers: DataStore<RegisteredUser> = new DataStore()
  public readonly authenticatedUsers: DataStore<AuthenticatedUser> = new DataStore()
  constructor(readonly config: BoosterConfig, readonly userProject: UserApp) {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async signUp(user: SignUpUser): Promise<void> {
    await this.userProject.boosterPreSignUpChecker(user)
    this.registeredUsers.insert({ ...user, confirmed: false })
  }

  public async signIn(user: LoginCredentials): Promise<UUID> {
    const token = UUID.generate()
    const registeredMatches = await this.getRegisteredUsersByEmail(user.username)
    if (registeredMatches.length === 0) {
      throw new NotAuthorizedError(`User with email ${user.username} has not been registered `)
    }
    const match = registeredMatches[0]
    if (!match.confirmed) {
      throw new NotAuthorizedError(`User with email ${user.username} has not been confirmed`)
    }
    if (!this.passwordsMatch(user, match)) {
      throw new NotAuthorizedError(`Wrong password for user with email ${user.username}`)
    }
    this.authenticatedUsers.insert({ username: user.username, token })
    return token
  }

  public async signOut(token: UUID): Promise<void> {
    this.authenticatedUsers.remove({ token })
  }

  public async confirmUser(email: string): Promise<void> {
    this.registeredUsers.update({ email }, { $set: { confirmed: true } })
  }

  private async getRegisteredUsersByEmail(email: string): Promise<Array<RegisteredUser>> {
    return promisify(this.registeredUsers.find({ email }).exec)()
  }

  private passwordsMatch(credentials: LoginCredentials, registered: RegisteredUser): boolean {
    return registered.password !== credentials.password
  }
}
