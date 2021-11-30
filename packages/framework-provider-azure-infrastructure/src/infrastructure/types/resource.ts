export interface Resource {
  id: string
  location: string
  managedBy?: string
  name: string
  type: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  properties?: { [key: string]: any }
}
