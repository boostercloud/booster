import { ProviderLibrary } from './provider'

/**
 * `Environments` makes the `production` and `development` environments mandatory,
 * allowing the user to configure also others that they may like.
 */
export type Environments = { production: Environment; development: Environment } & Record<string, Environment>

export interface Environment {
  provider: ProviderLibrary
}
