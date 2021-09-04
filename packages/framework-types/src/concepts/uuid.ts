import { v4 as uuid } from 'uuid'
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

/** Time-based unique identifier. It's a string in the form <timestamp>-<random UUID>.  */
export class TimeKey extends String {
  /**
   * Time-based unique identifier generator
   * @param moment Number of miliseconds since epoch for the moment in which the identifier should be generated. It defaults to the current time.
   * @returns A unique identifier in the form "<moment>-<random UUID>"
   */
  public static generate(moment = Date.now()): TimeKey {
    return `${moment}-${uuid()}`
  }
}
