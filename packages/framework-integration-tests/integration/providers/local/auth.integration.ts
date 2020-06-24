import { confirmUserURL, createUser, signUpURL } from './utils'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import { internet } from 'faker'

describe('With the auth API', () => {
  describe('sign-up', () => {
    context('new user', () => {
      let userEmail: string
      let userPassword: string

      beforeEach(() => {
        userEmail = internet.email()
        userPassword = internet.password()
      })

      it('should successfully register a new user', async () => {
        const response: Response = await fetch(signUpURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
            userAttributes: {
              roles: [],
            },
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.empty
        expect(response.status).to.equal(200)
      })
    })

    context('repeated user', () => {
      let userEmail: string
      let userPassword: string

      beforeEach(() => {
        userEmail = internet.email()
        userPassword = internet.password()
      })

      it('should return a 401 error, username already registered', async () => {
        // First user registration
        let response: Response = await fetch(signUpURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
            userAttributes: {
              roles: [],
            },
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        let message = await response.json()

        expect(message).to.be.empty
        expect(response.status).to.equal(200)

        // repeated user registration
        response = await fetch(signUpURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
            userAttributes: {
              roles: [],
            },
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        message = await response.json()

        expect(message).not.to.be.empty
        expect(message).to.be.deep.equal({
          title: 'Not Authorized Error',
          reason: `User with username ${userEmail} is already registered`,
        })
        expect(response.status).to.equal(401)
      })
    })
  })

  describe('confirm user', () => {
    let userEmail: string
    let userPassword: string

    beforeEach(() => {
      userEmail = internet.email()
      userPassword = internet.password()
    })

    context('registered username', () => {
      it('should successfully confirm user', async () => {
        await createUser(userEmail, userPassword)

        const response: Response = await fetch(confirmUserURL(userEmail), {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.equal('User confirmed!')
        expect(response.status).to.equal(200)
      })
    })

    context('not registered username', () => {
      let notRegisteredUserEmail: string

      beforeEach(() => {
        notRegisteredUserEmail = internet.email()
      })

      it('should return an error with expected message', async () => {
        const response: Response = await fetch(confirmUserURL(notRegisteredUserEmail), {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.deep.equal({
          title: 'Not Found Error',
          reason: `Incorrect username ${notRegisteredUserEmail}`,
        })
        expect(response.status).to.equal(404)
      })
    })
  })
})
