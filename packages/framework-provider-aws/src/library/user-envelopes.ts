import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelope } from '@boostercloud/framework-types'
import { APIGatewayProxyEvent } from 'aws-lambda'
import { CognitoIdentityServiceProvider } from 'aws-sdk'

export class UserEnvelopeBuilder {
  public static fromAttributeMap(attributes: AttributeMappingType): UserEnvelope {
    const { email, 'custom:roles': rolesString } = attributes
    return {
      email,
      roles: this.rolesStringToArray(rolesString),
    }
  }

  public static fromAttributeList(attributes: AttributeListType): UserEnvelope {
    return this.fromAttributeMap(this.attributeListToMap(attributes))
  }

  private static rolesStringToArray(rolesString: string): Array<string> {
    return rolesString ? rolesString.split(',').map((role) => role.trim()) : []
  }

  private static attributeListToMap(attributes: AttributeListType): AttributeMappingType {
    const attributeMap: AttributeMappingType = {}
    attributes.forEach((attrData) => {
      if (attrData.Value) {
        attributeMap[attrData.Name] = attrData.Value
      }
    })
    return attributeMap
  }
}

export async function fetchUserFromRequest(
  request: APIGatewayProxyEvent,
  userPool: CognitoIdentityServiceProvider
): Promise<UserEnvelope | undefined> {
  const accessToken = request.headers['Authorization']?.replace('Bearer ', '') // Remove the "Bearer" prefix
  if (!accessToken) {
    return undefined
  }
  const currentUserData = await userPool.getUser({ AccessToken: accessToken }).promise()
  return UserEnvelopeBuilder.fromAttributeList(currentUserData.UserAttributes)
}
