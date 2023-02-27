/* eslint-disable @typescript-eslint/no-explicit-any */
import { ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'

/**
 * `Provider` is a function that accepts a list of rocket names and returns an
 * object compatible with the `ProviderLibrary` defined in the `framework-types` package.
 * The rocket names are passed to the infrastructure package, which loads them dynamically
 * to extend the AWS functionality. Rockets are typically distributed in separate node packages.
 */
export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => {
  try {
    /**
     * We try to load the AWS SDK dynamically here because it is not included in the
     * production dependencies. Notice that this package is always present in AWS Lambda
     * environments and is not needed in any other environment.
     */
    require('aws-sdk')
    const { Provider } = require('./setup')
    return Provider(rockets)
  } catch (e) {
    // This only happens when running the project from an environment where the dependency is not needed
    return {} as ProviderLibrary
  }
}

export * from './constants'
