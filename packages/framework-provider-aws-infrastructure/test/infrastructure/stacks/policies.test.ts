import { createPolicyStatement } from '../../../src/infrastructure/stacks/policies'
import { expect } from 'chai'
import { Effect } from '@aws-cdk/aws-iam'
import { random } from 'faker'

describe('policies', () => {
  describe('createPolicyStatement', () => {
    let mockResources: string[]
    let mockActions: string[]

    beforeEach(() => {
      mockResources = [random.alphaNumeric(10)]
      mockActions = ['dynamodb:getItem']
    })

    it('should return expected policy statement', () => {
      const expectedResult = {
        action: mockActions,
        notAction: [],
        principal: {},
        notPrincipal: {},
        resource: mockResources,
        notResource: [],
        condition: {},
        effect: Effect.ALLOW,
      }

      const result = createPolicyStatement(mockResources, mockActions)

      expect(result).to.deep.equal(expectedResult)
    })

    describe('no resources and no actions', () => {
      it('should return expected policy statement', () => {
        const expectedResult = {
          action: [],
          notAction: [],
          principal: {},
          notPrincipal: {},
          resource: [],
          notResource: [],
          condition: {},
          effect: Effect.ALLOW,
        }

        const result = createPolicyStatement()

        expect(result).to.deep.equal(expectedResult)
      })
    })

    describe('no resources', () => {
      it('should return expected policy statement', () => {
        const expectedResult = {
          action: mockActions,
          notAction: [],
          principal: {},
          notPrincipal: {},
          resource: [],
          notResource: [],
          condition: {},
          effect: Effect.ALLOW,
        }

        const result = createPolicyStatement(undefined, mockActions)

        expect(result).to.deep.equal(expectedResult)
      })
    })

    describe('no actions', () => {
      it('should return expected policy statement', () => {
        const expectedResult = {
          action: [],
          notAction: [],
          principal: {},
          notPrincipal: {},
          resource: mockResources,
          notResource: [],
          condition: {},
          effect: Effect.ALLOW,
        }

        const result = createPolicyStatement(mockResources)

        expect(result).to.deep.equal(expectedResult)
      })
    })

    describe('invalid action', () => {
      it('should return expected policy statement', async () => {
        await expect(() => createPolicyStatement(mockResources, ['invalid-action'])).to.throw(
          "Action 'invalid-action' is invalid. An action string consists of a service namespace, a colon, and the name of an action. Action names can include wildcards."
        )
      })
    })

    describe('deny effect', () => {
      it('should return expected policy statement', () => {
        const expectedResult = {
          action: mockActions,
          notAction: [],
          principal: {},
          notPrincipal: {},
          resource: mockResources,
          notResource: [],
          condition: {},
          effect: Effect.DENY,
        }

        const result = createPolicyStatement(mockResources, mockActions, Effect.DENY)

        expect(result).to.deep.equal(expectedResult)
      })
    })
  })
})
