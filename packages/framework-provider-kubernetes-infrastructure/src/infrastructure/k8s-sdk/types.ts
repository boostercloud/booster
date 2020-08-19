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

export interface Service {
  name?: string
  namespace?: string
  kind?: string
  labels?: { [key: string]: string }
  status?: string
  ip?: string
}

export interface VolumeClaim {
  name?: string
  status?: string
  labels?: { [key: string]: string }
}

export interface Secret {
  name?: string
  data?: { [key: string]: string }
}
