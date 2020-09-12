import { UUID, NotAuthorizedError } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { registeredUsersDatabase, authenticatedUsersDatabase } from '../paths'
import { RegisteredUser, AuthenticatedUser, SignUpUser, LoginCredentials } from '../library/auth-adapter'
import { NotFoundError, UserEnvelope } from '@boostercloud/framework-types'

export class UserRegistry {
  public readonly registeredUsers: DataStore<RegisteredUser> = new DataStore(registeredUsersDatabase)
  public readonly authenticatedUsers: DataStore<AuthenticatedUser> = new DataStore(authenticatedUsersDatabase)

  public constructor() {
    this.registeredUsers.loadDatabase()
    this.authenticatedUsers.loadDatabase()
  }

  public async signUp(user: SignUpUser): Promise<SignUpUser> {
    const matches = await this.getRegisteredUsersByEmail(user.username)
    if (matches.length !== 0) throw new NotAuthorizedError(`User with username ${user.username} is already registered`)
    return new Promise((resolve, reject) => {
      this.registeredUsers.insert({ ...user, confirmed: false }, (err, doc) => {
        err ? reject(err) : resolve(doc)
      })
    })
  }

  public async signIn(user: LoginCredentials): Promise<UUID> {
    const match = await this.getUserByCredentials(user)
    if (!match) {
      throw new NotAuthorizedError('Incorrect username or password')
    }
    if (!match.confirmed) {
      throw new NotAuthorizedError(`User with username ${user.username} has not been confirmed`)
    }
    const token = UUID.generate()
    await this.authenticateUser(user, token)
    return token
  }

  private async authenticateUser(user: LoginCredentials, token: UUID): Promise<void> {
    return new Promise((resolve, reject) => {
      this.authenticatedUsers.insert({ username: user.username, token }, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async signOut(token: UUID): Promise<void> {
    return new Promise((resolve, reject) => {
      this.authenticatedUsers.remove({ token }, { multi: true }, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async confirmUser(username: string): Promise<void> {
    const registeredUsers = await this.getRegisteredUsersByEmail(username)

    return new Promise((resolve, reject) => {
      if (registeredUsers.length < 1) {
        reject(new NotFoundError(`Incorrect username ${username}`))
      }

      this.registeredUsers.update({ username }, { $set: { confirmed: true } }, {}, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  private async getRegisteredUsersByEmail(username: string): Promise<Array<RegisteredUser>> {
    return new Promise((resolve, reject) =>
      this.registeredUsers.find({ username }).exec((err, documents) => {
        err ? reject(err) : resolve(documents)
      })
    )
  }

  private async getUserByCredentials(user: LoginCredentials): Promise<RegisteredUser | undefined> {
    return new Promise((resolve, reject) => {
      this.registeredUsers.findOne({ ...user }, (err, doc) => {
        err ? reject(err) : resolve(doc)
      })
    })
  }

  public async getAuthenticatedUser(token: string): Promise<UserEnvelope> {
    return new Promise((resolve: (value: UserEnvelope) => void, reject) => {
      this.authenticatedUsers.findOne({ token }, (err, doc) => {
        if (err) {
          reject(err)
        } else {
          this.registeredUsers.findOne({ username: doc.username }, (err, doc) => {
            if (err) {
              reject(err)
            } else {
              resolve({
                username: doc.username,
                role: doc.userAttributes.role,
              })
            }
          })
        }
      })
    })
  }
}
