# Static Sites Rocket

This package is a configurable Booster rocket to add static site deployment to your Booster applications. It uploads your root.

:::info
[GitHub Repo](https://github.com/boostercloud/rocket-static-sites-aws-infrastructure)
:::

## Usage
 
 Install this package as a dev dependency in your Booster project (It's a dev dependency because it's only used during deployment, but we don't want this code to be uploaded to the project lambdas)

 ```bash
 npm install --save-dev @boostercloud/rocket-static-sites-aws-infrastructure
 ```

 In your Booster config file, pass a RocketDescriptor in the config.rockets array to configuring the static site rocket:

 ```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.rockets = [
    {
      packageName: '@boostercloud/rocket-static-sites-aws-infrastructure', 
      parameters: {
      bucketName: 'test-bucket-name', // Required
      rootPath: './frontend/dist', // Defaults to ./public
      indexFile: 'main.html', // File to render when users access the CLoudFormation URL. Defaults to index.html
      errorFile: 'error.html', // File to render when there's an error. Defaults to 404.html
      }
    },
  ]
})
 ```