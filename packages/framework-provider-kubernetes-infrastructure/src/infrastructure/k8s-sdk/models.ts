export interface Node {
  name?: string
  labels?: { [key: string]: string }
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
