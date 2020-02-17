import { BoosterConfig } from '@boostercloud/framework-types'
import { CfnOutput, RemovalPolicy, Stack } from '@aws-cdk/core'
import { AuthFlow, CfnUserPool, CfnUserPoolDomain, UserPoolAttribute, UserPoolClient } from '@aws-cdk/aws-cognito'
import { Code, Function } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { ServicePrincipal } from '@aws-cdk/aws-iam'

export class AuthStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): void {
    if (this.config.thereAreRoles) {
      const userPool = this.buildUserPool()
      this.buildUserPoolClient(userPool)
    }
  }

  private buildUserPool(): CfnUserPool {
    const localPreSignUpID = 'pre-sign-up-validator'
    const preSignUpLambda = new Function(this.stack, localPreSignUpID, {
      ...params.lambda,
      functionName: this.config.resourceNames.applicationStack + '-' + localPreSignUpID,
      handler: this.config.preSignUpHandler,
      code: Code.fromAsset(this.config.userProjectRootPath),
    })

    const localUserPoolID = 'user-pool'
    const userPool = new CfnUserPool(this.stack, localUserPoolID, {
      userPoolName: this.config.resourceNames.applicationStack + '-' + localUserPoolID,
      autoVerifiedAttributes: [UserPoolAttribute.EMAIL],
      schema: [
        {
          attributeDataType: 'String',
          mutable: true,
          name: 'roles',
        },
      ],
      usernameAttributes: [UserPoolAttribute.EMAIL],
      verificationMessageTemplate: {
        defaultEmailOption: 'CONFIRM_WITH_LINK',
      },
      lambdaConfig: {
        preSignUp: preSignUpLambda.functionArn,
      },
    })

    preSignUpLambda.addPermission(localPreSignUpID + '-user-pool-permission', {
      principal: new ServicePrincipal('cognito-idp.amazonaws.com'),
      sourceArn: userPool.attrArn,
    })

    const localUserPoolDomainID = 'user-pool-domain'
    new CfnUserPoolDomain(this.stack, localUserPoolDomainID, {
      userPoolId: userPool.ref,
      domain: this.config.resourceNames.applicationStack,
    }).applyRemovalPolicy(RemovalPolicy.DESTROY)

    return userPool
  }

  private buildUserPoolClient(userPool: CfnUserPool): void {
    // Usually, you have multiple clients: one for your WebApp, another for your MobileApp, etc.
    // We could allow defining how many clients the user wants. So far we just create one.
    const localUserPoolClientID = 'user-pool-client'
    const userPoolClient = new UserPoolClient(this.stack, localUserPoolClientID, {
      userPoolClientName: this.config.resourceNames.applicationStack + '-' + localUserPoolClientID,
      userPool: {
        node: userPool.node,
        stack: this.stack,
        userPoolArn: userPool.attrArn,
        userPoolId: userPool.ref,
        userPoolProviderName: userPool.attrProviderName,
        userPoolProviderUrl: userPool.attrProviderUrl,
      },
      enabledAuthFlows: [AuthFlow.USER_PASSWORD],
    })

    new CfnOutput(this.stack, 'clientID', {
      value: userPoolClient.userPoolClientId,
      description: 'Needed for the auth API. This ID must be included in that API under the name "clientID"',
    })
  }
}
