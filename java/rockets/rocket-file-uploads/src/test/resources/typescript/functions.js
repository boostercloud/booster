
const { app } = require('@azure/functions')
const { boosterRocketDispatcher } = require('./dist/index')

app.storageBlob('fileupload_clientst', {
  path: 'rocketfiles/{name}',
  connection: 'clientst',
  handler: async (blob, context) => {
    return await boosterRocketDispatcher({ blob, context })
  }
})


app.storageBlob('fileupload_cmsst', {
  path: 'rocketfiles/{name}',
  connection: 'cmsst',
  handler: async (blob, context) => {
    return await boosterRocketDispatcher({ blob, context })
  }
})
