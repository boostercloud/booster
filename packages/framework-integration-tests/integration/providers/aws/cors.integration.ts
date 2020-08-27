import { authClientID, createPassword, signInURL, signOutURL, signUpURL } from './utils'
import { internet } from 'faker'
import fetch from 'cross-fetch'
import { expect } from '@boostercloud/framework-provider-aws/test/expect'

describe('The Authentication API', () => {
  let clientId: string
  const methodsToCheck = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH']
  const preflightOptions = generatePreflightOptionsList(methodsToCheck)

  before(async () => {
    clientId = await authClientID()
  })

  context('/auth/sign-up', () => {
    let signUpUrl: string
    let validSignUpBody: string
    const invalidSignUpBody = JSON.stringify({})

    before(async () => {
      signUpUrl = await signUpURL()
      validSignUpBody = JSON.stringify({
        clientId: clientId,
        username: internet.email(),
        password: createPassword(),
        userAttributes: {
          role: 'UserWithEmail',
        },
      })
    })

    context('OPTIONS', () => {
      it('should allow all the headers and methods regardless the requests values', async () => {
        const responses = await Promise.all(preflightOptions.map(performPreflightRequest(signUpUrl)))

        responses.forEach(assertResponseContainsPreflightHeaders)
      })
    })

    context('POST', () => {
      it('should return the Access-Control-Allow-Origin header for 200 responses', async () => {
        const response = await fetch(signUpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'Application/json',
          },
          body: validSignUpBody,
        })

        expect(response.status).to.be.eq(200, `Response body was: ${JSON.stringify(await response.json())}`)
        expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
      })

      it('should return the Access-Control-Allow-Origin header for 400 responses', async () => {
        const response = await fetch(signUpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'Application/json',
          },
          body: invalidSignUpBody,
        })

        expect(response.status).to.be.eq(400)
        expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
      })
      it('should return the Access-Control-Allow-Origin header for 500 responses')
    })
  })

  context('/auth/sign-in', () => {
    let signInUrl: string

    before(async () => {
      signInUrl = await signInURL()
    })

    context('OPTIONS', () => {
      it('should allow all the headers and methods regardless the requests values', async () => {
        const responses = await Promise.all(preflightOptions.map(performPreflightRequest(signInUrl)))

        responses.forEach(assertResponseContainsPreflightHeaders)
      })
    })
  })

  context('/auth/sign-out', () => {
    let signOutUrl: string

    before(async () => {
      signOutUrl = await signOutURL()
    })

    context('OPTIONS', () => {
      it('should allow all the headers and methods regardless the requests values', async () => {
        const responses = await Promise.all(preflightOptions.map(performPreflightRequest(signOutUrl)))

        responses.forEach(assertResponseContainsPreflightHeaders)
      })
    })
  })

  function generatePreflightOptionsList(desiredHttpMethods: string[]): RequestInit[] {
    // For more info about preflight requests see: https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request
    return desiredHttpMethods.map(
      (method: string): RequestInit => ({
        method: 'OPTIONS',
        headers: {
          'Access-Control-Request-Method': method,
          'Access-Control-Request-Headers': 'X-any-header',
          Origin: internet.url(),
        },
      })
    )
  }

  function performPreflightRequest(url: string) {
    return (options: RequestInit) => fetch(url, options)
  }

  function assertResponseContainsPreflightHeaders(response: Response) {
    expect(response.status).to.be.eq(204)
    expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
    expect(response.headers.get('Access-Control-Allow-Headers')).to.be.eq('*')
    expect(response.headers.get('Access-Control-Allow-Methods'))
      .to.include('OPTIONS')
      .and.to.include('POST')
  }
})
