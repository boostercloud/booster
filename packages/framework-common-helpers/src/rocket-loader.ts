import { RocketDescriptor } from '@boostercloud/framework-types'

export class RocketLoader {
  // Separate function to make it easier to mock in tests
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private static requireRocket(rocketPackageName: string): (params: unknown) => any {
    return require(rocketPackageName).default
  }

  // eslint-disable-next-line @typescript-eslint/ban-types
  public static loadRocket(rocketDescriptor: RocketDescriptor): { mountStack: Function } {
    const rocketBuilder = RocketLoader.requireRocket(rocketDescriptor.packageName)
    if (!rocketBuilder)
      throw new Error(
        `Could not load the rocket infrastructure package ${rocketDescriptor.packageName}. Make sure you installed it in your project devDependencies.`
      )
    if (typeof rocketBuilder !== 'function')
      throw new Error(
        `Could not initialize rocket infrastructure package ${rocketDescriptor.packageName}. It doesn't seem to implement a rocket builder method as default export.`
      )
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rocket = rocketBuilder(rocketDescriptor.parameters)
    if (!rocket?.mountStack) throw new Error(`The package ${rocketDescriptor.packageName} doesn't seem to be a rocket.`)
    return rocket
  }
}
