export type PartialRocketDescriptor = {
  packageName: string
  parameters: unknown
}

export type RocketDescriptor<TProviderContext> = PartialRocketDescriptor & {
  context: TProviderContext
}

export function withContext<TProviderContext>(
  context: TProviderContext,
  partialRocketDescriptors: Array<PartialRocketDescriptor>
): Array<RocketDescriptor<TProviderContext>> {
  return partialRocketDescriptors.map((partialRocketDescriptor) => {
    return { ...partialRocketDescriptor, context }
  })
}
