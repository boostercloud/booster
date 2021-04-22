import * as express from 'express'
import * as cors from 'cors'
import * as bodyParser from 'body-parser'
import 'isomorphic-fetch'
import * as path from 'path'
import { UserApp } from '@boostercloud/framework-types'

const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
const port = 3000
const app = express()

app.use(bodyParser.json())
app.use(cors())
app.use(function(req, res, next) {
  res.header('Access-Control-Allow-Origin', '*')
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept')
  next()
})
app.post('/graphQL', async (req, res) => {
  try {
    const response = await userProject.boosterServeGraphQL(req)
    res.status(response.statusCode).json(response.body)
  } catch (e) {
    console.error(e)
  }
})
app.get('/ready', async (req, res) => {
  res.send('ok')
})
app.listen(port, () => console.log('Node App started!'))
