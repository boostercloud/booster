import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelope } from '@boostercloud/framework-types'
import { CognitoIdentityServiceProvider } from 'aws-sdk'

export class UserEnvelopeBuilder {
  public static fromAttributeMap(attributes: AttributeMappingType): UserEnvelope {
    // eslint-disable-next-line @typescript-eslint/camelcase
    const { phone_number, email, 'custom:role': role } = attributes
    // eslint-disable-next-line @typescript-eslint/camelcase
    const username = email ? { value: email, type: 'email' } : { value: phone_number, type: 'phone' }

    return {
      username,
      role: role ? role.trim() : '',
    }
  }

  public static fromAttributeList(attributes: AttributeListType): UserEnvelope {
    return this.fromAttributeMap(this.attributeListToMap(attributes))
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

type CouldHaveHeaders = { headers: { [name: string]: string } | null }
export async function fetchUserFromRequest(
  userPool: CognitoIdentityServiceProvider,
  request: CouldHaveHeaders,
  graphQLValueAuthorization?: string,
): Promise<UserEnvelope | undefined> {
  const accessToken = getTokenFromRequest(request) ?? graphQLValueAuthorization
  if (!accessToken) {
    return undefined
  }
  const currentUserData = await userPool.getUser({ AccessToken: accessToken }).promise()
  return UserEnvelopeBuilder.fromAttributeList(currentUserData.UserAttributes)
}

function getTokenFromRequest(request: CouldHaveHeaders): string | undefined {
  return request.headers?.['Authorization']?.replace('Bearer ', '') // Remove the "Bearer" prefix
}
