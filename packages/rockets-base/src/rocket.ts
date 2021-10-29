export interface Rocket<TProviderContext> {
  mount: (context: TProviderContext) => Promise<void>
  unmount: (context: TProviderContext) => Promise<void>
}
