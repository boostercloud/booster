export interface Template {
  name: string
  template: string
}

export interface TemplateValues {
  timestamp?: string
  namespace: string
  clusterVolume: string
  environment: string
  serviceType: string
  dbHost?: string
  dbUser?: string
  eventStoreSecretName?: string
  eventStoreSecretKey?: string
}

export interface DaprTemplateValues {
  namespace: string
  eventStoreHost: string
  eventStoreUsername: string
  eventStoreSecretName: string
  eventStoreSecretKey: string
}

export interface DaprTemplateRoles {
  namespace: string
}
