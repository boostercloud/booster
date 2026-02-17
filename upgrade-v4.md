# Upgrade from Booster v3.x.x to Booster v4.x.x

Booster v4 introduces the following breaking changes:

## 1. Node 22 Support

Booster v4 has been upgraded to work with Node 22. If you're using an older version of Node, you'll need to upgrade it.

```bash
# Check your current Node version
node --version

# Upgrade to Node 22 using nvm (recommended)
nvm install 22
nvm use 22
```

## 2. Azure Functions v4 Programming Model (Breaking Change)

This is the most significant change in Booster v4. The Azure provider has been migrated from the Azure Functions v3 programming model to the **Azure Functions v4 programming model**.

### What Changed?

The v4 programming model introduces a fundamentally different way of registering and defining Azure Functions:

- **No more `function.json` files**: Functions are now registered programmatically using `app.*()` methods from `@azure/functions`.
- **New trigger and binding syntax**: The way triggers and bindings are defined has changed significantly.
- **Updated runtime**: The Azure Functions runtime now uses Node 22 with the v4 model.

### Migration Steps

1. **Update your Booster dependencies** to v4.x.x in your `package.json`:
   ```json
   {
     "dependencies": {
       "@boostercloud/framework-core": "^4.0.0",
       "@boostercloud/framework-provider-azure": "^4.0.0",
       "@boostercloud/framework-provider-azure-infrastructure": "^4.0.0",
       "@boostercloud/framework-types": "^4.0.0"
     }
   }
   ```

2. **Redeploy your application**: Since the function registration model has changed, you'll need to redeploy your application completely.

## 3. Rockets Must Be Updated (Breaking Change)

**This is critical**: If you are using any Rockets in your Booster application, they **must be updated** to be compatible with Booster v4 and the Azure Functions v4 programming model.

### Why Rockets Need Updating

Rockets that add Azure Functions (such as webhooks, file uploads, or custom endpoints) previously relied on generating `function.json` files. With the v4 programming model, Rockets must now:

1. **Use programmatic function registration**: Instead of generating `function.json` files, Rockets must use the `app.*()` registration API.
2. **Implement the `mountFunctionsV4` method**: Rockets must implement the new `mountFunctionsV4` method (replacing the deprecated `mountCode` and `mountFunctions` methods) to provide their function registration code.
3. **Update trigger and binding definitions**: All triggers and bindings must use the new v4 syntax.

### Checking Rocket Compatibility

Before upgrading, verify that all Rockets you're using have been updated for Booster v4:

| Rocket | Minimum Version for v4 |
|--------|------------------------|
| `@boostercloud/rocket-webhook` | Check the rocket's repository for v4-compatible version |
| `@boostercloud/rocket-file-uploads` | Check the rocket's repository for v4-compatible version |
| `@boostercloud/rocket-static-sites` | Check the rocket's repository for v4-compatible version |
| `@boostercloud/rocket-backup-booster` | Check the rocket's repository for v4-compatible version |

### For Rocket Authors: Migration Guide

If you maintain a custom Rocket, here's how to update it for Booster v4:

#### Before (v3 - function.json based):

Previously, Rockets would generate `function.json` files:

```json
{
  "bindings": [
    {
      "type": "httpTrigger",
      "direction": "in",
      "name": "req",
      "methods": ["post"]
    },
    {
      "type": "http",
      "direction": "out",
      "name": "res"
    }
  ]
}
```

#### After (v4 - programmatic registration):
```typescript
// Now, Rockets must provide code for programmatic registration
import { app } from '@azure/functions'

app.http('myWebhook', {
  methods: ['POST'],
  authLevel: 'anonymous',
  handler: async (request, context) => {
    // Handle the request
    return { status: 200, body: 'OK' }
  }
})
```

### Key Changes for Rocket Implementation

1. **Update the `rocketForAzure()` method**: Ensure it returns infrastructure compatible with the v4 model.

2. **Implement `mountFunctionsV4`** (replaces deprecated `mountCode` and `mountFunctions`):
   ```typescript
   import { InfrastructureRocket } from '@boostercloud/framework-provider-azure-infrastructure'
   import { FunctionAppV4Definitions } from '@boostercloud/framework-provider-azure-infrastructure'

   const MyRocket = (params: MyRocketParams): InfrastructureRocket => ({
     mountStack: async (config, applicationSynthStack, utils) => {
       // Infrastructure provisioning
       return applicationSynthStack
     },
     mountFunctionsV4: async (config, applicationSynthStack, utils): Promise<FunctionAppV4Definitions> => {
       // Return an array of function app definitions with v4 registration code
       return [
         {
           functionAppName: applicationSynthStack.functionApp.name,
           functionsCode: `
             app.http('myEndpoint', {
               methods: ['POST'],
               authLevel: 'anonymous',
               handler: async (request, context) => {
                 return await myHandler(request, context)
               }
             })
           `,
           hostJsonPath: undefined, // Optional: path to custom host.json
         },
       ]
     },
   })
   ```

3. **Update any direct Azure Functions SDK usage** to use `@azure/functions` v4 APIs.

## 4. Additional Considerations

### Azure Infrastructure

- Ensure your Azure Function App is configured to use Node 22 runtime.
- The Function App must support the v4 programming model.

### Local Development

- Update your local development environment to Node 22.
- The local provider has been updated to work with the new model.

### Testing

After upgrading:
1. Run your integration tests to ensure all functionality works correctly.
2. Test all Rocket-provided endpoints.
3. Verify scheduled commands are executing as expected.
4. Check that subscriptions and WebSocket connections work properly.

## Need Help?

If you encounter issues during the upgrade:
- Check the [Booster documentation](https://docs.boosterframework.com)
- Open an issue on [GitHub](https://github.com/boostercloud/booster)
- Reach out via the official channels listed on the [Booster website](https://boosterframework.com)

