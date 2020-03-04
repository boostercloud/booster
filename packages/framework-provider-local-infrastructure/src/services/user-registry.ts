import { UserEnvelope, UUID, BoosterConfig, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { promisify } from 'util'

type User = UserEnvelope & {
  password: string
  confirmed: boolean
  token: UUID
}

type LoginCredentials = Pick<User, 'email' | 'password'>
type SignUpUser = Pick<User, 'email' | 'password' | 'roles'>
type RegisteredUser = Pick<User, 'email' | 'password' | 'roles' | 'confirmed'>
type SignInUser = Pick<User, 'email' | 'token'>

export class UserRegistry {
  public readonly registeredUsers: DataStore<RegisteredUser> = new DataStore()
  public readonly authenticatedUsers: DataStore<SignInUser> = new DataStore()
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

  public async confirmUser(email: string): Promise<void> {
    this.registeredUsers.update({ email }, { $set: { confirmed: true } })
  }

  private async getRegisteredUsersByEmail(email: string): Promise<Array<SignUpUser>> {
    return promisify(this.registeredUsers.find({ email }).exec)()
  }
}
