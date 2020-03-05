import uuid = require('uuid/v4')
/**
 * `UUID` type to work globally as a identifier for Entities,
 * Commands, Events or any other booster artifact.
 * New unique identifiers can be created using the
 * `UUID.generate` method.
 */
export class UUID extends String {
  public static generate(): UUID {
    return uuid()
  }
}
