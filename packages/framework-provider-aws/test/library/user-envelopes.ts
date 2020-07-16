/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { UserEnvelope } from '@boostercloud/framework-types'
import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { fetchUserFromRequest, UserEnvelopeBuilder } from '../../src/library/user-envelopes'
import { APIGatewayProxyEvent } from 'aws-lambda'
import CognitoIdentityServiceProvider = require('aws-sdk/clients/cognitoidentityserviceprovider')
import { restore, replace, fake } from 'sinon'

describe('the UserEnvelopeBuilder.fromAttributeMap', () => {
  it('works with a user with no roles', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
    }

    const expected: UserEnvelope = {
      email: input.email,
      roles: [],
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })

  it('works with a user with empty roles', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
      'custom:roles': '',
    }

    const expected: UserEnvelope = {
      email: input.email,
      roles: [],
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })

  it('works with a user with one role', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
      'custom:roles': 'Admin',
    }

    const expected: UserEnvelope = {
      email: input.email,
      roles: ['Admin'],
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })

  it('works with a user with several roles', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
      'custom:roles': 'Admin,User,Tester,SalesAgent',
    }

    const expected: UserEnvelope = {
      email: input.email,
      roles: ['Admin', 'User', 'Tester', 'SalesAgent'],
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })
})

describe('the UserEnvelopeBuilder.fromAttributeList', () => {
  it('works with a user with roles', () => {
    const input: AttributeListType = [
      {
        Name: 'email',
        Value: 'test@user.com',
      },
      {
        Name: 'custom:roles',
        Value: 'Admin,User',
      },
    ]

    const expected: UserEnvelope = {
      email: 'test@user.com',
      roles: ['Admin', 'User'],
    }

    const got = UserEnvelopeBuilder.fromAttributeList(input)

    expect(got).to.deep.equal(expected)
  })
})

describe('the fetchUserFromRequest function', () => {
  afterEach(() => {
    restore()
  })

  it('returns nothing when no token is provided', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const apiEvent: APIGatewayProxyEvent = {
      headers: {},
    } as any

    await expect(fetchUserFromRequest(userPool, apiEvent)).to.be.eventually.equal(undefined)
  })

  it('returns a user when the corresponding token is provided', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const token = '123'
    const userReturnedFromPool: AttributeListType = [
      {
        Name: 'email',
        Value: 'test@user.com',
      },
      {
        Name: 'custom:roles',
        Value: 'Admin,User',
      },
    ]
    const expectedUser = {
      email: 'test@user.com',
      roles: ['Admin', 'User'],
    }

    replace(
      userPool,
      'getUser',
      fake.returns({
        promise: fake.resolves({
          UserAttributes: userReturnedFromPool,
        }),
      })
    )
    const apiEvent: APIGatewayProxyEvent = {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    } as any
    await expect(fetchUserFromRequest(userPool, apiEvent)).to.be.eventually.deep.equal(expectedUser)
    expect(userPool.getUser).to.have.been.calledWith({
      AccessToken: token,
    })
  })
})
