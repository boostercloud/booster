export interface Pod {
  name?: string
  namespace?: string
  kind?: string
  labels?: { [key: string]: string }
  status?: string
  nodeName?: string
  ip?: string
}
