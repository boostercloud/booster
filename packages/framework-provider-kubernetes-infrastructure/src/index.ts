import { ProviderInfrastructure } from '@boostercloud/framework-types'
import { deploy, nuke } from './infrastructure'

export const Infrastructure = (): ProviderInfrastructure => {
  return {
    deploy,
    nuke,
  }
}
