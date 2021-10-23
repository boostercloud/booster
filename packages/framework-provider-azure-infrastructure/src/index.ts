import { deploy, nuke } from './infrastructure'
export * from './test-helper/azure-test-helper'

export const Infrastructure = {
  deploy,
  nuke,
}
