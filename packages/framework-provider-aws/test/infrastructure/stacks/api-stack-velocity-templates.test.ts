import * as Velocity from 'velocityjs'
import { CognitoTemplates } from '../../../src/infrastructure/stacks/api-stack-velocity-templates'
import { expect } from 'chai'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function buildAwsTemplateContext(withData: Record<string, any>): Record<string, any> {
  return {
    input: {
      path: () => withData,
    },
  }
}

describe('CognitoTemplates.SignUp.request', () => {
  it('returns the Cognito expected input for a user without roles', () => {
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

  it('returns the Cognito expected input for a user with ONE role', () => {
    const input = {
      clientId: 'a-client-id',
      username: 'user@name.com',
      password: 'test_password',
      userAttributes: {
        roles: ['Admin'],
      },
    }
    const expectedOutput = {
      ClientId: input.clientId,
      Username: input.username,
      Password: input.password,
      UserAttributes: [
        {
          Name: 'custom:roles',
          Value: 'Admin',
        },
      ],
    }

    const context = buildAwsTemplateContext(input)
    const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signUp.request, context))

    expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
  })

  it('returns the Cognito expected input for a user with MULTIPLE roles and other attributes', () => {
    const input = {
      clientId: 'a-client-id',
      username: 'user@name.com',
      password: 'test_password',
      userAttributes: {
        name: 'Test user',
        roles: ['Admin', 'Sales', 'Business'],
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
          Name: 'custom:roles',
          Value: 'Admin,Sales,Business',
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

describe('CognitoTemplates.SignUp.response', () => {
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

describe('CognitoTemplates.SignIn.request', () => {
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

describe('CognitoTemplates.SignIn.response', () => {
  it('returns the Booster expected output', () => {
    const input = {
      AuthenticationResult: {
        AccessToken: 'access-token',
        ExpiresIn: '123456',
        RefreshToken: 'refresh-token',
        TokenType: 'Bearer',
      },
    }
    const expectedOutput = {
      accessToken: input.AuthenticationResult.AccessToken,
      expiresIn: input.AuthenticationResult.ExpiresIn,
      refreshToken: input.AuthenticationResult.RefreshToken,
      tokenType: input.AuthenticationResult.TokenType,
    }

    const context = buildAwsTemplateContext(input)
    const gotOutputJSON = JSON.parse(Velocity.render(CognitoTemplates.signIn.response, context))

    expect(gotOutputJSON).to.be.deep.equal(expectedOutput)
  })
})

describe('CognitoTemplates.SignOut.request', () => {
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

describe('CognitoTemplates.SignOut.response', () => {
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
