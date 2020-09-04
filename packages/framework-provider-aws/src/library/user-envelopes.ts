import { AttributeListType, AttributeMappingType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { UserEnvelope } from '@boostercloud/framework-types'

export class UserEnvelopeBuilder {
  public static fromAttributeMap(attributes: AttributeMappingType): UserEnvelope {
    // eslint-disable-next-line @typescript-eslint/camelcase
    const { phone_number, email, 'custom:role': role } = attributes
    // eslint-disable-next-line @typescript-eslint/camelcase
    const username = email ? email : phone_number

    return {
      username,
      role: role?.trim() ?? '',
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
