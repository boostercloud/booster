/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { UserEnvelope } from '@boostercloud/framework-types'
import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelopeBuilder } from '../../src/library/user-envelopes'

describe('the UserEnvelopeBuilder.fromAttributeMap', () => {
  it('works with a user with no role', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
    }

    const expected: UserEnvelope = {
      username: input.email,
      role: '',
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })

  it('works with a user with empty role', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
      'custom:role': '',
    }

    const expected: UserEnvelope = {
      username: input.email,
      role: '',
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })

  it('works with a user with one role', () => {
    const input: AttributeMappingType = {
      email: 'test@user.com',
      'custom:role': 'Admin',
    }

    const expected: UserEnvelope = {
      username: input.email,
      role: 'Admin',
    }
    const got = UserEnvelopeBuilder.fromAttributeMap(input)
    expect(got).to.be.deep.equal(expected)
  })
})

describe('the UserEnvelopeBuilder.fromAttributeList', () => {
  it('works with a user with one role', () => {
    const input: AttributeListType = [
      {
        Name: 'email',
        Value: 'test@user.com',
      },
      {
        Name: 'custom:role',
        Value: 'User',
      },
    ]

    const expected: UserEnvelope = {
      username: 'test@user.com',
      role: 'User',
    }

    const got = UserEnvelopeBuilder.fromAttributeList(input)

    expect(got).to.deep.equal(expected)
  })
})
