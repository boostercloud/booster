import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelope } from '@boostercloud/framework-types'

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
