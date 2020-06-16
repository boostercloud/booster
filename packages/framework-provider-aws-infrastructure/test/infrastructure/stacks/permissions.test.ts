import { Function } from '@aws-cdk/aws-lambda'
import { setupPermissions } from '../../../src/infrastructure/stacks/permissions'
import { Table } from '@aws-cdk/aws-dynamodb'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { SinonStub, assert, stub, restore } from 'sinon'
import { random } from 'faker'
import * as policies from '../../../src/infrastructure/stacks/policies'
import { PolicyStatement } from '@aws-cdk/aws-iam'
import { Fn } from '@aws-cdk/core'

describe('permissions', () => {
  beforeEach(() => {
    restore()
  })

  describe('setupPermissions', () => {
    let mockSubscriptionsTableArn: string
    let mockReadModelTableArn: string
    let mockEventsStoreTableArn: string
    let mockWebSocketAPIRef: string
    let mockPolicyStatement: PolicyStatement
    let mockFnJoin: string
    let mockFnRef: string

    let mockGraphQLLambda: Function
    let mockSubscriptionDispatcherLambda: Function
    let mockEventsLambda: Function
    let mockSubscriptionsTable: Table
    let mockEventsStoreTable: Table
    let mockReadModelTable: Table
    let mockReadModelTables: Array<Table>
    let mockWebsocketAPI: CfnApi

    let createPolicyStatementStub: SinonStub
    let graphQLAddToRolePolicyStub: SinonStub
    let subscriptionAddToRolePolicyStub: SinonStub
    let eventsAddToRolePolicyStub: SinonStub

    let fnJoinStub: SinonStub
    let fnRefStub: SinonStub

    beforeEach(() => {
      mockSubscriptionsTableArn = random.alphaNumeric(10)
      mockReadModelTableArn = random.alphaNumeric(10)
      mockEventsStoreTableArn = random.alphaNumeric(10)
      mockWebSocketAPIRef = random.alphaNumeric(10)
      mockPolicyStatement = new PolicyStatement()
      mockFnJoin = random.alphaNumeric(10)
      mockFnRef = random.alphaNumeric(10)

      createPolicyStatementStub = stub(policies, 'createPolicyStatement').returns(mockPolicyStatement)
      graphQLAddToRolePolicyStub = stub()
      subscriptionAddToRolePolicyStub = stub()
      eventsAddToRolePolicyStub = stub()

      mockGraphQLLambda = {} as Function
      mockSubscriptionDispatcherLambda = {} as Function
      mockEventsLambda = {} as Function

      mockSubscriptionsTable = {
        tableArn: mockSubscriptionsTableArn,
      } as Table
      mockEventsStoreTable = {
        tableArn: mockEventsStoreTableArn,
      } as Table
      mockReadModelTable = {
        tableArn: mockReadModelTableArn,
      } as Table
      mockReadModelTables = [mockReadModelTable]

      mockWebsocketAPI = {
        ref: mockWebSocketAPIRef,
      } as CfnApi

      mockGraphQLLambda.addToRolePolicy = graphQLAddToRolePolicyStub
      mockSubscriptionDispatcherLambda.addToRolePolicy = subscriptionAddToRolePolicyStub
      mockEventsLambda.addToRolePolicy = eventsAddToRolePolicyStub

      fnJoinStub = stub(Fn, 'join').returns(mockFnJoin)
      fnRefStub = stub(Fn, 'ref').returns(mockFnRef)

      setupPermissions(
        mockReadModelTables,
        mockGraphQLLambda,
        mockSubscriptionDispatcherLambda,
        mockSubscriptionsTable,
        mockWebsocketAPI,
        mockEventsStoreTable,
        mockEventsLambda
      )
    })

    describe('GraphQL Lambda', () => {
      it('should call addToRolePolicy', () => {
        assert.callCount(graphQLAddToRolePolicyStub, 4)

        assert.calledWithExactly(graphQLAddToRolePolicyStub, mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should add events store permissions', () => {
          assert.calledWithExactly(
            createPolicyStatementStub,
            [mockEventsStoreTableArn],
            ['dynamodb:BatchWriteItem', 'dynamodb:Query*', 'dynamodb:Put*']
          )
        })

        it('should create subscriptions table permissions', () => {
          assert.calledWithExactly(createPolicyStatementStub, [mockSubscriptionsTableArn], ['dynamodb:Put*'])
        })

        it('should create read model permissions', () => {
          assert.calledWithExactly(
            createPolicyStatementStub,
            [mockReadModelTableArn],
            ['dynamodb:Query*', 'dynamodb:Scan*']
          )
        })
      })
    })

    describe('Subscriptions Lambda', () => {
      it('should call addToRolePolicy', () => {
        assert.calledTwice(subscriptionAddToRolePolicyStub)

        assert.calledWithExactly(subscriptionAddToRolePolicyStub, mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should create subscriptions table permissions', () => {
          assert.calledWithExactly(createPolicyStatementStub, [mockSubscriptionsTableArn], ['dynamodb:Query*'])
        })

        describe('web socket API', () => {
          it('should call Fn Join', () => {
            assert.calledOnce(fnJoinStub)
            assert.calledWithExactly(fnJoinStub, ':', [
              'arn',
              mockFnRef,
              'execute-api',
              mockFnRef,
              mockFnRef,
              `${mockWebSocketAPIRef}/*`,
            ])
          })

          it('should call Fn Ref', () => {
            assert.calledThrice(fnRefStub)
          })

          it('should create web socket API permissions', () => {
            assert.calledWithExactly(createPolicyStatementStub, [mockFnJoin], ['execute-api:ManageConnections'])
          })
        })
      })
    })

    describe('Events Lambda', () => {
      it('should call addToRolePolicy', () => {
        assert.calledTwice(eventsAddToRolePolicyStub)

        assert.calledWithExactly(eventsAddToRolePolicyStub, mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should add events store permissions', () => {
          assert.calledWithExactly(
            createPolicyStatementStub,
            [mockEventsStoreTableArn],
            ['dynamodb:BatchWriteItem', 'dynamodb:Query*', 'dynamodb:Put*']
          )
        })

        it('should create read model permissions', () => {
          assert.calledWithExactly(
            createPolicyStatementStub,
            [mockReadModelTableArn],
            ['dynamodb:Get*', 'dynamodb:Put*']
          )
        })
      })
    })
  })
})
