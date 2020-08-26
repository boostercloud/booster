import { signUpURL } from './utils'
import { internet } from 'faker'
import fetch from 'cross-fetch'
import { expect } from '@boostercloud/framework-provider-aws/test/expect'

describe('The Authentication API', () => {
  context('/auth/sign-up', () => {
    context('sending OPTIONS', () => {
      function methodToPreflightOptions() {
        return (methodToPreflight: string): RequestInit => {
          return {
            method: 'OPTIONS',
            headers: {
              'Access-Control-Request-Method': methodToPreflight,
              'Access-Control-Request-Headers': 'X-any-header',
              Origin: internet.url(),
            },
          }
        }
      }
      const httpMethodsToPreflight = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH']

      it('should allow all the headers and methods regardless the requests values', async () => {
        const optionsList = httpMethodsToPreflight.map(methodToPreflightOptions())
        const url = await signUpURL()

        const responses = await Promise.all(optionsList.map((options) => fetch(url, options)))

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
  })
})
