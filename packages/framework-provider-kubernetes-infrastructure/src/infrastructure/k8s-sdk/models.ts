export interface Node {
  name?: string
  labels?: { [key: string]: string }
  role?: string
  ip?: string
  mainNode: boolean
}

export interface Namespace {
  name?: string
  status?: string
  labels?: { [key: string]: string }
}

export interface Pod {
  name?: string
  namespace?: string
  kind?: string
  labels?: { [key: string]: string }
  status?: string
  nodeName?: string
  ip?: string
}

export interface TemplateValues {
  namespace?: string
  clusterVolume?: string
  environment?: string
}
