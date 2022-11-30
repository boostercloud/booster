# Testing Booster applications

To properly test a Booster application, you should create a `test` folder at the same level as the `src` one. Apart from that, tests' names should have the `<my_test>.test.ts` format.

When a Booster application is generated, you will have a script in a `package.json` like this:
```typescript
"scripts": {
  "test": "nyc --extension .ts mocha --forbid-only \"test/**/*.test.ts\""
}
```

The only thing that you should add to this line are the `AWS_SDK_LOAD_CONFIG=true` and `BOOSTER_ENV=test` environment variables, so the script will look like this:

```typescript
"scripts": {
  "test": "set AWS_SDK_LOAD_CONFIG=true & set BOOSTER_ENV=test & nyc --extension .ts mocha --forbid-only \"test/**/*.test.ts\""
}
```

### Testing with `sinon-chai`
The `BoosterConfig` can be accessed through the `Booster.config` on any part of a Booster application. To properly mock it for your objective, we really recommend to use sinon `replace` method, after configuring your `Booster.config` as desired.

In the example below, we add 2 "empty" read-models, since we are iterating `Booster.config.readModels` from a command handler:

```typescript
// Test
import { replace } from 'sinon'

const config = new BoosterConfig('test')
config.appName = 'testing-time'
config.providerPackage = '@boostercloud/framework-provider-aws'
config.readModels['WoW'] = {} as ReadModelMetadata
config.readModels['Amazing'] = {} as ReadModelMetadata
replace(Booster, 'config', config)

const spyMyCall = spy(MyCommand, 'myCall')
const command = new MyCommand('1', true)
const register = new Register('request-id-1')
const registerSpy = spy(register, 'events')
await MyCommand.handle(command, register)

expect(spyMyCall).to.have.been.calledOnceWithExactly('WoW')
expect(spyMyCall).to.have.been.calledOnceWithExactly('Amazing')
expect(registerSpy).to.have.been.calledOnceWithExactly(new MyEvent('1', 'WoW'))
expect(registerSpy).to.have.been.calledOnceWithExactly(new MyEvent('1', 'Amazing'))
```

```typescript
// Example code
public static async handle(command: MyCommand, register: Register): Promise<void> {
  const readModels = Booster.config.readModels
  for (const readModelName in readModels) {
    myCall(readModelName)
    register.events(new MyEvent(command.ID, readModelName))
  }
}
```

### Recommended files
These are some files that might help you speed up your testing with Booster.

```typescript
// <root_dir>/test/expect.ts
import * as chai from 'chai'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

export const expect = chai.expect
```
This `expect` method will help you with some more additional methods like `expect(<my_stub>).to.have.been.calledOnceWithExactly(<my_params..>)`

```yaml
# <root_dir>/.mocharc.yml
diff: true
require: 'ts-node/register'
extension:
  - ts
package: './package.json'
recursive: true
reporter: 'spec'
timeout: 5000
full-trace: true
bail: true
```