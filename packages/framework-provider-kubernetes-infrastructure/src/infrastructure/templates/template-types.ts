export interface Template {
  name: string
  template: string
}

export interface TemplateValues {
  timestamp?: string
  namespace: string
  clusterVolume: string
  environment: string
}

export interface DaprTemplateValues {
  namespace: string
  eventStoreHost: string
  eventStoreUsername: string
  eventStorePassword: string
}
