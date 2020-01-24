export * from './parsing'
export * from './types'

export interface Target<TInfo> {
  name: string
  extension: string
  placementDir: string
  template: string
  info: TInfo
}
