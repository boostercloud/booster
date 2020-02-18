import * as express from 'express'

export function deploy(port: number): void {
  const expressServer = express()
  const router = express.Router()
  expressServer.use(express.json())
  expressServer.use(router)
  expressServer.listen(port)
}
