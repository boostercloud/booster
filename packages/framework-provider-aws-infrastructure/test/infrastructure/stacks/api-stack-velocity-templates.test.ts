import * as Velocity from 'velocityjs'
import { CognitoTemplates } from '../../../src/infrastructure/stacks/api-stack-velocity-templates'
import { expect } from '../../expect'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function buildAwsTemplateContext(withData: Record<string, any>): Record<string, any> {
  return {
    input: {
      path: () => withData,
    },
  }
}

describe('CognitoTemplates', () => {
  describe('SignUp', () => {
    describe('request', () => {
      it('returns the Cognito expected input for a user without role', () => {
        const input = {
          clientId: 'a-client-id',
          username: 'user@name.com',
          password: 'test_password',
        }
        const expectedOutput = {
          ClientId: input.clientId,
          Username: input.username,
          Password: input.password,
          UserAttributes: [],
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signUp.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })

      it('returns the Cognito expected input for a user with ONE role and other attributes', () => {
        const input = {
          clientId: 'a-client-id',
          username: 'user@name.com',
          password: 'test_password',
          userAttributes: {
            name: 'Test user',
            role: 'Admin',
            age: 30,
          },
        }
        const expectedOutput = {
          ClientId: input.clientId,
          Username: input.username,
          Password: input.password,
          UserAttributes: [
            {
              Name: 'name',
              Value: 'Test user',
            },
            {
              Name: 'custom:role',
              Value: 'Admin',
            },
            {
              Name: 'age',
              Value: '30',
            },
          ],
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signUp.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })

    describe('response', () => {
      it('returns an empty json object', () => {
        const input = {
          any: 'field',
          other: 'value',
        }
        const expectedOutput = {}

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signUp.response, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
  })

  describe('ConfirmSignUp', () => {
    describe('request', () => {
      it('returns the Cognito expected input', () => {
        const input = {
          clientId: 'a-client-id',
          confirmationCode: '12345',
          username: 'user@name.com',
        }
        const expectedOutput = {
          ClientId: input.clientId,
          ConfirmationCode: input.confirmationCode,
          Username: input.username,
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.confirmSignUp.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
    describe('response', () => {
      it('returns an empty json object', () => {
        const input = {
          any: 'field',
          other: 'value',
        }
        const expectedOutput = {}

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.confirmSignUp.response, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
  })

  describe('SignIn', () => {
    describe('request', () => {
      it('returns the Cognito expected input', () => {
        const input = {
          clientId: 'a-client-id',
          username: 'user@name.com',
          password: 'test_password',
        }
        const expectedOutput = {
          AuthFlow: 'USER_PASSWORD_AUTH',
          ClientId: input.clientId,
          AuthParameters: {
            USERNAME: input.username,
            PASSWORD: input.password,
          },
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signIn.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })

    describe('response', () => {
      it('returns the Booster expected output', () => {
        const input = {
          AuthenticationResult: {
            AccessToken: 'access-token',
            IdToken: 'id-token',
            ExpiresIn: '123456',
            RefreshToken: 'refresh-token',
            TokenType: 'Bearer',
          },
        }
        const expectedOutput = {
          accessToken: input.AuthenticationResult.AccessToken,
          idToken: input.AuthenticationResult.IdToken,
          expiresIn: input.AuthenticationResult.ExpiresIn,
          refreshToken: input.AuthenticationResult.RefreshToken,
          tokenType: input.AuthenticationResult.TokenType,
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signIn.response, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
  })

  describe('SignOut', () => {
    describe('request', () => {
      it('returns the Cognito expected input', () => {
        const input = {
          accessToken: 'access-token',
        }
        const expectedOutput = {
          AccessToken: input.accessToken,
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signOut.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })

    describe('response', () => {
      it('returns an empty json object', () => {
        const input = {
          any: 'field',
          other: 'value',
        }
        const expectedOutput = {}

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signOut.response, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
  })

  describe('RefreshToken', () => {
    describe('request', () => {
      it('returns the Cognito expected input', () => {
        const input = {
          clientId: 'a-client-id',
          refreshToken: 'access-token',
        }
        const expectedOutput = {
          AuthFlow: 'REFRESH_TOKEN_AUTH',
          ClientId: input.clientId,
          AuthParameters: {
            REFRESH_TOKEN: input.refreshToken,
          },
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.refreshToken.request, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })

    describe('response', () => {
      it('returns the Booster expected output', () => {
        const input = {
          AuthenticationResult: {
            AccessToken: 'access-token',
            IdToken: 'id-token',
            ExpiresIn: '123456',
            RefreshToken: 'refresh-token',
            TokenType: 'Bearer',
          },
        }
        const expectedOutput = {
          accessToken: input.AuthenticationResult.AccessToken,
          idToken: input.AuthenticationResult.IdToken,
          expiresIn: input.AuthenticationResult.ExpiresIn,
          refreshToken: input.AuthenticationResult.RefreshToken,
          tokenType: input.AuthenticationResult.TokenType,
        }

        const context = buildAwsTemplateContext(input)
        const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.refreshToken.response, context))

        expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
      })
    })
  })
})
