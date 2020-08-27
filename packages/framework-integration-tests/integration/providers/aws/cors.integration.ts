import {authClientID, createPassword, signUpURL} from './utils'
import {internet} from 'faker'
import fetch from 'cross-fetch'
import {expect} from '@boostercloud/framework-provider-aws/test/expect'

describe('The Authentication API', () => {
  let clientId: string
  before(async () => {
    clientId = await authClientID()
  });

  context('/auth/sign-up', () => {
    let signUpUrl: string
    let validSignUpBody: string;
    const invalidSignUpBody = JSON.stringify({})

    before(async () => {
      signUpUrl = await signUpURL();
      validSignUpBody = JSON.stringify({
        clientId: clientId,
        username: internet.email(),
        password: createPassword(),
        userAttributes: {
          role: 'UserWithEmail',
        },
      });
    });

    context('OPTIONS', () => {
      function methodToPreflightOptions(methodToPreflight: string): RequestInit {
        return {
          method: 'OPTIONS',
          headers: {
            'Access-Control-Request-Method': methodToPreflight,
            'Access-Control-Request-Headers': 'X-any-header',
            Origin: internet.url(),
          },
        }
      }

      const httpMethodsToPreflight = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH']

      it('should allow all the headers and methods regardless the requests values', async () => {
        const optionsList = httpMethodsToPreflight.map(methodToPreflightOptions)

        const responses = await Promise.all(optionsList.map((options) => fetch(signUpUrl, options)))

        responses.forEach((response) => {
          expect(response.status).to.be.eq(204)
          expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
          expect(response.headers.get('Access-Control-Allow-Headers')).to.be.eq('*')
          expect(response.headers.get('Access-Control-Allow-Methods'))
            .to.include('OPTIONS')
            .and.to.include('POST')
        })
      })
    })

    context('POST', () => {
      it('should return the Access-Control-Allow-Origin header for 200 responses', async () => {
        const response = await fetch(signUpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'Application/json'
          },
          body: validSignUpBody,
        })

        expect(response.status).to.be.eq(200, `Response body was: ${JSON.stringify((await response.json()))}`)
        expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
      })

      it('should return the Access-Control-Allow-Origin header for 400 responses', async () => {
        const response = await fetch(signUpUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'Application/json'
          },
          body: invalidSignUpBody,
        })

        expect(response.status).to.be.eq(400)
        expect(response.headers.get('Access-Control-Allow-Origin')).to.be.eq('*')
      })
      it('should return the Access-Control-Allow-Origin header for 500 responses')
    })
  })
})
