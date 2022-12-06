# Authentication and Authorization

Booster accepts standard [JWT tokens](https://jwt.io/) to authenticate incoming requests. Likewise, you can use the claims included in these tokens to authorize access to commands or read models by using the provided simple role-based authorization or writing your own authorizer functions.

:::note
To learn how to include the access token in your requests, check the section [Authorizing operations](#authorizing-operations).
:::

## Validating incoming tokens (Authentication)

In order to validate incoming tokens and make sure that user requests come from trustable origins, you need to provide one or more `TokenVerifier` instances at config time for each of your environments. Booster provides standard implementations for jwskUri and public key based authentication:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'demoapp'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.tokenVerifiers = [
    new JwskUriTokenVerifier(
      'https://securetoken.google.com/demoapp',
      'https://demoapp.firebase.com/.well-known/jwks.json',
      'firebase:groups'
    ),
    new PublicKeyTokenVerifier(
      'custom-key-pair',
      getCustomKey(),
      'custom:roles'
    ),
  ]
})
```

### JWKS URI based authorization

One common way to validate JWT tokens is by using a issuer-provided well-known URI on which you can find their [JWK](https://datatracker.ietf.org/doc/html/rfc7517) sets (JWKS). If you use this method, you only need to provide the issuer's name, the JWKS URI and, if you're using role-based authentication, an optional `rolesClaim` option that sets the claim from which Booster will read the role names.

```typescript
...
config.tokenVerifiers = [
  new JwskUriTokenVerifier(
    'https://securetoken.google.com/demoapp', // Issuer name
    'https://demoapp.firebase.com/.well-known/jwks.json', // JWKS URI that points to the issuer's well-known JWKS JSON file
    'firebase:groups', // Name of the claim to read the roles from (when you're using role-based authorization)
  ),
]
...
```

### Public key based authentication

If the token issuer doesn't provide a JWKS URI, you can also validate tokens against a known public key. One scenario where this is useful is when you're implementing your own authentication mechanism or you're issuing self-signed tokens.

:::note
If you need to handle private keys in production, consider using a KMS [(Key Management System)](https://en.wikipedia.org/wiki/Key_management#Key_storage)). These systems often provide API endpoints that let you encrypt/sign your JWT tokens without exposing the private keys. The public keys can be set in a `PublicKeyTokenVerifier` to automate verification.
:::

```typescript
config.tokenVerifiers = [
  new PublicKeyTokenVerifier(
    'custom-key-pair', // Issuer name
    getCustomKey(), // Promise that resolves to the public key string
    'custom:roles', // Name of the claim to read the roles from (when you're using role-based authorization)
  ),
]
```

Notice that the `publicKeyResolver` is a promise that resolves to a string, so it can be used to load the public key from a remote location too (i.e. get it from your KMS).

### Custom authentication

You can also provide your own `TokenVerifier` implementation for advanced acceptance criteria beyond simple cryptographic signature checks. An use case for this could be to check that the token was generated specifically for your service by inspecting the `aud` claim, or check that the token has not been blacklisted or invalidated by your business logic (i.e. a user logs out before the token's expiration date and is included in an invalidated tokens list to make sure that an attacker that finds the token later can't use it to impersonate the legitimate owner).

Booster will accept as a token verifier any object that matches the `TokenVerifier` interface:

```typescript
export interface TokenVerifier {
  // Verify and deserialize a stringified token with this token verifier.
  verify(token: string): Promise<DecodedToken>
  // Build a valid `UserEnvelope` from a decoded token.
  toUserEnvelope(decodedToken: DecodedToken): UserEnvelope
}
```

If you only need to perform extra validations on top of one of the default `TokenVerifier`s, you can extend one of the default implementations:

```typescript
export class CustomValidator extends PrivateKeyValidator {
  public async verify(token: string): Promise<UserEnvelope> {
    // Call to the PrivateKeyValidator verify method to check the signature
    const userEnvelope = await super.verify(token)

    // Do my extra validations here. Throwing an error will reject the token
    await myExtraValidations(userEnvelope.claims, token)

    return userEnvelope
  }
}
```

If you need to do more advanced checks, you can implement the whole verification algorithm yourself (this could make sense if you're using non-standard or legacy tokens). Booster exposes for convenience many of the utility functions that it uses in the default `TokenVerifier` implementations:

```typescript
/**
 * Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the
 * `getKeyWithClient` function.
 */
export function getJwksClient(jwksUri: string) {
  ...
}

/**
 * Initializes a function that can be used to get the public key from a JWKS URI with the signature
 * required by the `verifyJWT` function. You can create a client using the `getJwksClient` function.
 */
export function getKeyWithClient(
  client: jwksRSA.JwksClient,
  header: jwt.JwtHeader,
  callback: jwt.SigningKeyCallback
): void {
  ...
}

/**
 * Verifies a JWT token using a key or key resolver function and returns a Booster UserEnvelope.
 */
export async function verifyJWT(
  token: string,
  issuer: string,
  key: jwt.Secret | jwt.GetPublicKeyOrSecret,
  rolesClaim?: string
): Promise<UserEnvelope> {
 ...
}
```

## Checking when a valid user can perform a specific actions (Authentication)

Every Command and ReadModel in Booster has an `authorize` policy that tells Booster who can access it. Booster authorization follows a whitelisting approach, so all read models and commands are inaccessible by default unless you define a different behavior. The `authorize` parameter accepts one of the following options:

- `'all'`: The command or read-model is explicitly public: any user, both authenticated and anonymous, can access it.
- An array of authorized roles `[Role1, Role2, ...]`: This means that only those authenticated users that
  have any of the roles listed there are authorized to execute the command.
- An authorizer function that matches the `CommandAuthorizer` interface for commands or the `ReadModelAuthorizer` interface for read models.

### `authorize: 'all'`: Making commands and read models public

Setting the option `authorize: all` in a command or read model will make it publicly accessible to anyone that has access to the graphql endpoint. For example, the following command can be executed by anyone, even if they don't provide a valid JWT token:

```typescript
@Command({
  authorize: 'all',
})
export class CreateComment {
  ...
}
```

:::note
**Think twice if you really need fully open GraphQL endpoints in your application**, this might be useful during development, but we recommend to **avoid exposing your endpoints in this way in production**. Even for public APIs, it might be useful to issue API keys to avoid abuse. Booster is designed to scale to any given demand, but scaling also increases the cloud bill! (See [Denial of wallet attacks](https://www.sciencedirect.com/science/article/pii/S221421262100079X))
:::

### Simple Role-based authorization

Booster provides a simple role-based authentication mechanism that will work in many standard scenarios. As many other Booster artifacts, Roles are defined as simple decorated classes, typically in (but not limited to) the `src/config/roles.ts` file. To define a role, you only need to decorate an empty class with the `@Role` decorator as follows:

```typescript
@Role()
export class User {}

@Role()
export class Admin {}
```

Once they're defined you can set them in any command or read model `authorize` policy. For instance, the following command can be executed by authenticated users that have the role `Admin` or `User` and will reject any request from users that don't have valid JWT tokens or have valid tokens but doesn't have one of the roles' names in their token (Remember that the name of the claim from which Booster reads the roles from can be configured in the `roleClaims` option in the corresponding `TokenVerifier`):

```typescript
@Command({
  authorize: [Admin, User],
})
export class UpdateUser {
  ...
}
```

Remember to also configure your JWT tokens issuer to include the custom claims required in your Booster app (If you're not using the provider default one). For instance, for the previous `UpdateUser` command, Booster will expect to receive a token that includes a claim matching the one defined in the `TokenVerifier`'s `rolesClaim` property with the value `Admin` or `User`. Here is an example from a Firebase token:

```json
{
  "firebase:groups": "User", // <- roles are read from 'firebase:groups' claim
  "iss": "https://securetoken.google.com/demoapp",
  "aud": "demoapp",
  "auth_time": 1604676721,
  "user_id": "xJY5Y6fTbVggNtDjaNh7cNSBd7q1",
  "sub": "xJY5Y6fTbVggNtDjaNh7cNSBd7q1",
  "iat": 1604676721,
  "exp": 1604680321,
  "phone_number": "+999999999",
  "firebase": {}
}
```

#### Extended roles when using the [Authentication Booster Rocket for AWS](https://github.com/boostercloud/rocket-auth-aws-infrastructure)

The Authentication Rocket for AWS is an opinionated implementation of a JWT tokens issuer on top of AWS Cognito that includes out-of-the-box features like sign-up, sign-in, passwordless tokens, change password and many other features. When a user goes through the sign up and sign in mecanisms provided by the rocket, they'll get a standard JWT access token that can be included in any request as a Bearer token and will work in the same way as any other JWT token.

When you use this rocket, you can use extra configuration parameters in the `@Role` decorator to enable some of these features. In the following example we define `Admin`, `User`, `SuperUser` and `SuperUserWithoutConfirmation` roles. They all contain an extra `auth` configuration attribute that set the behavior of the authorization role for each role:

```typescript
@Role({
  auth: {
    signUpMethods: [], // Using an empty array here prevents sign-ups (Admin has no special treatment. If you don't enable signup, you'll need to create the first admin manually in the AWS console)
  },
})
export class Admin {}

@Role({
  auth: {
    signUpMethods: ['email'], // Enable email sign-ups for Users
  },
})
export class User {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'], // Can sign up by email or phone
    skipConfirmation: false, // It requires email or phone confirmation. The rocket will send either an email or a SMS with a confirmation link.
  },
})
export class SuperUser {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    skipConfirmation: true, // It doesn't require email or phone confirmation
  },
})
export class SuperUserWithoutConfirmation {}
```

To learn more about the Authorization rocket for AWS, please read the [README](https://github.com/boostercloud/rocket-auth-aws-infrastructure/blob/main/README.md) in its Github repository.

## Accessing the event streams API

You can enable access (disabled by default) to one or more entities' events streams through the events API. To do so, you just need to add a configuration object setting the `authorizeReadEvents` policy with any of the supported authorization mechanisms (`'all'` to make them public, an array of roles, or an authorizer function that matches the `EventStreamAuthorizer` type signature). For example:

```typescript
@Entity({
  authorizeReadEvents: 'all', // Anyone can read any Cart's event
})
export class Cart {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public checks = 0
  ) {}
  // <reducers...>
}
```

:::note
Be careful when exposing events data, as this data is likely to hold internal system state. Pay special attention when authorizing public access with the `'all'` option, it's always recommended to look for alternate solutions that limit access.
:::

### Custom authorization with authorizer functions

If the role-based authorization model doesn't work for your application (for instance, when your application requires permission-based authorization), you can implement your own authorization mechanisms by providing authorizer functions to commands, read models or entities (to access event streams). As authorizers are regular JavaScript functions, you can easily reuse them in your project or even in other Booster projects as a library.

#### Command Authorizers

Command authorizers are functions that match the `CommandAuthorizer` type:

```typescript
export type CommandAuthorizer = (currentUser?: UserEnvelope, input?: CommandInput) => Promise<void>
```

For instance, if you want to restrict a command to users that have a permission named `Permission-To-Rock` in the `permissions` claim you can do this:

```typescript
@Command({
  authorize: async (currentUser) => {
    if (!currentUser.claims['permissions'].includes('Permission-To-Rock')) {
      throw new Error(`User ${currentUser.username} should not be rocking!`)
    }
  }
})
export class PerformIncredibleGuitarSolo {
  ...
}
```

#### Read Model Authorizers

Read Model Authorizers are functions that match the `ReadModelAuthorizer` type:

```typescript
export type ReadModelAuthorizer<TReadModel extends ReadModelInterface> = (
  currentUser?: UserEnvelope,
  readModelRequestEnvelope?: ReadModelRequestEnvelope<TReadModel>
) => Promise<void>
```

For instance, you may want to restrict access to a specific resource only to people that has been granted read permission:

```typescript
@ReadModel({
  authorize: async (currentUser, readModelRequestEnvelope) => {
    const userPermissions = Booster.entity(UserPermissions, currentUser.username)
    if (!userPermissions || !userPermissions.accessTo[readModelRequestEnvelope.className].includes(readModelRequestEnvelope.key.id)) {
      throw new Error(`User ${currentUser.username} should not be looking here`)
    }
  }
})
```

#### Event Stream Authorizers

Event Stream Authorizers are functions that match the `EventStreamAuthorizer` type:

```typescript
export type EventStreamAuthorizer = (
  currentUser?: UserEnvelope,
  eventSearchRequest?: EventSearchRequest
) => Promise<void>
```

For instance, you can restrict access to entities that the current user own.

```typescript
@Entity({
  authorizeReadEvents: (currentUser, eventSearchRequest) => {
    const { entityID } = eventSearchRequest.parameters
    if (!entityID) {
      throw new Error(`${currentUser.username} cannot list carts`)
    }
    const cart = Booster.entity(Cart, entityID)
    if (cart.ownerUserName !== currentUser.userName) {
      throw new Error(`${currentUser.username} cannot see events in cart ${entityID}`)
    }
  }
})
export class Cart {
  public constructor(
    readonly id: UUID,
    readonly ownerUserName: string,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public checks = 0
  ) {}
  ...
}
```