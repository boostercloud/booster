import { deploy, nuke } from './infrastructure'
import { ProviderInfrastructure } from '@boostercloud/framework-types'

export const Infrastructure = (): ProviderInfrastructure => {
  return {
    deploy,
    nuke,
  }
}
