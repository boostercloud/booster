/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from 'chai'
import * as chai from 'chai'
import { UserEnvelope } from '@boostercloud/framework-types'
import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelopeBuilder } from '../../src/library/user-envelope-builder'

chai.use(require('sinon-chai'))

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
