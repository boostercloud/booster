import * as chai from 'chai'
import { createPassword, signInURL, signOutURL, signUpURL } from './helpers/utils'
import { internet } from 'faker'
import fetch from 'cross-fetch'

chai.use(require('chai-as-promised'))
const expect = chai.expect

describe('Given the Authentication API', () => {
  const username = internet.email()
  const password = createPassword()
  const role = 'SuperUserNoConfirmation'

  context('When /auth/sign-up', () => {
    let signUpUrl: string
    let validAuthBody: string
    const invalidAuthBody = JSON.stringify({})
    const methodsToCheck = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH']
    const preflightOptions = generatePreflightOptionsList(methodsToCheck)

    before(async () => {
      signUpUrl = await signUpURL()
      validAuthBody = JSON.stringify({
        username,
        password,
        userAttributes: {
          role,
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
          body: validAuthBody,
        })

        await verifyResponseAndAllowedOriginHeader(response, 200, '*', await response.json())
      })

      it('should return the Access-Control-Allow-Origin header for 400 responses', async () => {
        const response = await fetch(signUpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'Application/json',
          },
          body: invalidAuthBody,
        })

        await verifyResponseAndAllowedOriginHeader(response, 400, '*', await response.json())
      })
    })

    context('And then /auth/sign-in', () => {
      let signInUrl: string
      let accessToken: string

      before(async () => {
        signInUrl = await signInURL()
      })

      context('OPTIONS', () => {
        it('should allow all the headers and methods regardless the requests values', async () => {
          const responses = await Promise.all(preflightOptions.map(performPreflightRequest(signInUrl)))

          responses.forEach(assertResponseContainsPreflightHeaders)
        })
      })

      context('POST', () => {
        it('should return the Access-Control-Allow-Origin header for 200 responses', async () => {
          const response = await fetch(signInUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'Application/json' },
            body: validAuthBody,
          })
          const jsonBody = await response.json()
          accessToken = jsonBody['accessToken']

          await verifyResponseAndAllowedOriginHeader(response, 200, '*', jsonBody)
        })
        it('should return the Access-Control-Allow-Origin header for 400 responses', async () => {
          const response = await fetch(signInUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'Application/json' },
            body: invalidAuthBody,
          })
          // LOL
          await verifyResponseAndAllowedOriginHeader(response, 500, '*', await response.json())
        })
      })

      context('And then /auth/sign-out', () => {
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

        context('POST', () => {
          it('should return the Access-Control-Allow-Origin header for 200 responses', async () => {
            const response = await fetch(signOutUrl, {
              method: 'POST',
              headers: { 'Content-Type': 'Application/json' },
              body: JSON.stringify({
                accessToken: accessToken,
              }),
            })

            await verifyResponseAndAllowedOriginHeader(response, 200, '*', await response.json())
          })
          it('should return the Access-Control-Allow-Origin header for 400 responses', async () => {
            const response = await fetch(signOutUrl, {
              method: 'POST',
              headers: { 'Content-Type': 'Application/json' },
              body: invalidAuthBody,
            })

            await verifyResponseAndAllowedOriginHeader(response, 500, '*', await response.json())
          })
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

    function assertResponseContainsPreflightHeaders(response: Response): void {
      expect(response.status).to.be.eq(204)
      expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
      expect(response.headers.get('Access-Control-Allow-Headers')).to.be.eq('*')
      expect(response.headers.get('Access-Control-Allow-Methods'))
        .to.include('OPTIONS')
        .and.to.include('POST')
    }

    async function verifyResponseAndAllowedOriginHeader(
      response: Response,
      expectedHttpStatus: number,
      expectedAllowedOrigin: string,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      jsonBody: any
    ): Promise<void> {
      expect(response.status).to.be.eq(expectedHttpStatus, `Response body was: ${JSON.stringify(jsonBody)}`)
      expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq(expectedAllowedOrigin)
    }
  })
})
