export class AzureCounters {
  resourceGroupName: string
  constructor(resourceGroupName: string) {
    this.resourceGroupName = resourceGroupName
  }
  //TODO Azure Does not support Subscriptions
  public async subscriptions(): Promise<number> {
    return 0
  }

  //TODO Azure Does not support Subscriptions
  public async connections(): Promise<number> {
    return 0
  }

  //TODO we should implement this for provider unaware functionality tests
  public async events(): Promise<number> {
    return 0
  }

  //TODO we should implement this for provider unaware functionality tests
  public async readModels(): Promise<number> {
    return 0
  }
}
