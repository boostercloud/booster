import { ProviderTestHelper } from './provider-test-helper'

export class HttpHelper {
  constructor(private providerTestHelper: ProviderTestHelper) {}
  public getHealthUrl(): string {
    return this.providerTestHelper.outputs.healthURL
  }
}
