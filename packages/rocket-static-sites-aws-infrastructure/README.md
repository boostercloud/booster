# Static Sites Booster Rocket for AWS

This package is a configurable Booster rocket to add static site deployment to your Booster applications. It uploads your root.

## Usage

Install this package as a general dependency in your machine to allow the Booster CLI load it dynamically.

```sh
npm install -g @boostercloud/rocket-static-sites-aws-infrastructure
```

In your Booster config file, add a `RocketDescriptor` to the `config.rockets` configuration option with the configuration of the static site rocket as follows:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Provider
  config.rockets = [{
    packageName: '@boostercloud/rocket-static-sites-aws-infrastructure', 
    parameters: {
      bucketName: 'test-bucket-name', // Required
      rootPath: './frontend/dist', // Defaults to ./public
      indexFile: 'main.html', // File to render when users access the CLoudFormation URL. Defaults to index.html
      errorFile: 'error.html', // File to render when there's an error. Defaults to 404.html
    }
  }]
})
```
