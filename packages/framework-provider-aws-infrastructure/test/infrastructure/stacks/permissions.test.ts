import { Function } from '@aws-cdk/aws-lambda'
import { setupPermissions } from '../../../src/infrastructure/stacks/permissions'
import { Table } from '@aws-cdk/aws-dynamodb'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { SinonStub, stub, restore } from 'sinon'
import { expect } from '../../expect'
import { random } from 'faker'
import * as policies from '../../../src/infrastructure/stacks/policies'
import { PolicyStatement } from '@aws-cdk/aws-iam'
import { Fn } from '@aws-cdk/core'
import { GraphQLStackMembers } from '../../../src/infrastructure/stacks/graphql-stack'
import { EventsStackMembers } from '../../../src/infrastructure/stacks/events-stack'
import { BoosterConfig } from '@boostercloud/framework-types'

describe('permissions', () => {
  afterEach(() => {
    restore()
  })

  describe('setupPermissions', () => {
    let mockSubscriptionsStoreArn: string
    let mockConnectionsStoreArn: string
    let mockReadModelTableArn: string
    let mockEventsStoreTableArn: string
    let mockWebSocketAPIRef: string
    let mockPolicyStatement: PolicyStatement
    let mockFnJoin: string
    let mockFnRef: string

    let mockGraphQLLambda: Function
    let mockSubscriptionDispatcherLambda: Function
    let mockEventsLambda: Function
    let mockSubscriptionsStore: Table
    let mockConnectionsStore: Table
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

    const config = new BoosterConfig('test')
    config.appName = 'testing-app'
    config.userProjectRootPath = '.'

    beforeEach(() => {
      mockSubscriptionsStoreArn = random.alphaNumeric(10)
      mockConnectionsStoreArn = random.alphaNumeric(10)
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

      mockSubscriptionsStore = {
        tableArn: mockSubscriptionsStoreArn,
      } as Table
      mockConnectionsStore = {
        tableArn: mockConnectionsStoreArn,
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

      const graphQLStackMembers: GraphQLStackMembers = {
        subscriptionNotifier: mockSubscriptionDispatcherLambda,
        subscriptionsStore: mockSubscriptionsStore,
        graphQLLambda: mockGraphQLLambda,
        connectionsStore: mockConnectionsStore,
      }
      const eventsStackMembers: EventsStackMembers = {
        eventsStore: mockEventsStoreTable,
        eventsLambda: mockEventsLambda,
      }

      setupPermissions(config, graphQLStackMembers, eventsStackMembers, mockReadModelTables, mockWebsocketAPI)
    })

    describe('GraphQL Lambda', () => {
      it('should call addToRolePolicy', () => {
        expect(graphQLAddToRolePolicyStub).to.has.callCount(7)

        expect(graphQLAddToRolePolicyStub).calledWithExactly(mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should add events store permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockEventsStoreTableArn],
            ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchGetItem', 'dynamodb:BatchWriteItem']
          )
          expect(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn + '*'], ['dynamodb:Query*'])
        })

        it('should create subscriptions table permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockSubscriptionsStoreArn],
            ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:DeleteItem', 'dynamodb:BatchWriteItem']
          )
          expect(createPolicyStatementStub).calledWithExactly([mockSubscriptionsStoreArn + '*'], ['dynamodb:Query*'])
        })

        it('should create connections table permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockConnectionsStoreArn],
            ['dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:DeleteItem']
          )
        })

        it('should create read model permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockReadModelTableArn],
            ['dynamodb:Query*', 'dynamodb:Scan*']
          )
        })
      })
    })

    describe('Subscriptions Lambda', () => {
      it('should call addToRolePolicy', () => {
        expect(subscriptionAddToRolePolicyStub).to.be.calledTwice
        expect(subscriptionAddToRolePolicyStub).to.be.calledWithExactly(mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should create subscriptions table permissions', () => {
          expect(createPolicyStatementStub).to.be.calledWithExactly([mockSubscriptionsStoreArn], ['dynamodb:Query*'])
        })

        describe('web socket API', () => {
          it('should call Fn Join', () => {
            expect(fnJoinStub).to.be.calledOnce
            expect(fnJoinStub).to.be.calledWithExactly(':', [
              'arn',
              mockFnRef,
              'execute-api',
              mockFnRef,
              mockFnRef,
              `${mockWebSocketAPIRef}/*`,
            ])
          })

          it('should call Fn Ref', () => {
            expect(fnRefStub).to.be.calledThrice
          })

          it('should create web socket API permissions', () => {
            expect(createPolicyStatementStub).calledWithExactly([mockFnJoin], ['execute-api:ManageConnections'])
          })
        })
      })
    })

    describe('Events Lambda', () => {
      it('should call addToRolePolicy', () => {
        expect(eventsAddToRolePolicyStub).calledThrice
        expect(eventsAddToRolePolicyStub).calledWithExactly(mockPolicyStatement)
      })

      describe('policy statements', () => {
        it('should add events store permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockEventsStoreTableArn],
            ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchGetItem', 'dynamodb:BatchWriteItem']
          )
          expect(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn + '*'], ['dynamodb:Query*'])
        })

        it('should create read model permissions', () => {
          expect(createPolicyStatementStub).calledWithExactly(
            [mockReadModelTableArn],
            ['dynamodb:Get*', 'dynamodb:Query*', 'dynamodb:Scan*', 'dynamodb:Put*', 'dynamodb:DeleteItem*']
          )
        })
      })
    })
  })
})
