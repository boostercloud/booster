import { confirmUser, confirmUserURL, createUser, signOutURL, signUpURL } from './utils'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import { internet, random } from 'faker'
import { signInURL } from './utils'

// FIXME: When JWT auth is merged
xdescribe('With the auth API', () => {
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
              role: '',
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
              role: '',
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
              role: '',
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

      it('should return a 404 error with expected message', async () => {
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

  describe('sign-in', () => {
    context('invalid username', () => {
      let userEmail: string
      let userPassword: string

      beforeEach(() => {
        userEmail = internet.email()
        userPassword = internet.password()
      })

      it('should return a 401 error with expected message', async () => {
        const response: Response = await fetch(signInURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.deep.equal({
          title: 'Not Authorized Error',
          reason: 'Incorrect username or password',
        })
        expect(response.status).to.equal(401)
      })
    })

    context('not confirmed username', () => {
      let userEmail: string
      let userPassword: string

      beforeEach(async () => {
        userEmail = internet.email()
        userPassword = internet.password()

        await createUser(userEmail, userPassword)
      })

      it('should return a 401 error with expected message', async () => {
        const response: Response = await fetch(signInURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.deep.equal({
          title: 'Not Authorized Error',
          reason: `User with username ${userEmail} has not been confirmed`,
        })
        expect(response.status).to.equal(401)
      })
    })

    context('valid confirmed username', () => {
      let userEmail: string
      let userPassword: string

      beforeEach(async () => {
        userEmail = internet.email()
        userPassword = internet.password()

        await createUser(userEmail, userPassword)
        await confirmUser(userEmail)
      })

      it('should sign-in successfully', async () => {
        const response: Response = await fetch(signInURL(), {
          method: 'POST',
          body: JSON.stringify({
            username: userEmail,
            password: userPassword,
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).not.to.be.empty
        expect(response.status).to.equal(200)
      })
    })
  })

  describe('sign-out', () => {
    context('missing token', () => {
      it('should return a 400 error with expected message', async () => {
        const response: Response = await fetch(signOutURL(), {
          method: 'POST',
          body: JSON.stringify({}),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.equal('accessToken field not set')
        expect(response.status).to.equal(400)
      })
    })

    context('token provided', () => {
      it('should successfully sign-out', async () => {
        const response: Response = await fetch(signOutURL(), {
          method: 'POST',
          body: JSON.stringify({
            accessToken: random.uuid(),
          }),
          headers: {
            'Content-Type': 'application/json',
          },
        })

        const message = await response.json()

        expect(message).to.be.equal('')
        expect(response.status).to.equal(200)
      })
    })
  })
})
