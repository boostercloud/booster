---
description: Booster uses the OAuth 2.0 protocol to authenticate users. This section explains how to configure it.
---

# Authentication

Booster uses the OAuth 2.0 protocol to authenticate users. That means that it uses tokens to identify users and authorize them. These tokens are called _access tokens_ and are issued by an _authentication provider_. The most common authentication provider is [Auth0](https://auth0.com/), but you can use any other provider that supports OAuth 2.0.

## Configuring the authentication provider

The first step to configure authentication in Booster is to configure the authentication provider. The provider must support OAuth 2.0 and must be able to issue _access tokens_. In order to validate incoming tokens and make sure that user requests come from trustable origins, you need to provide one or more `TokenVerifier` instances at config time for each of your environments.

The `TokenVerifier` class is a simple interface that you can implement to define your own token verifiers. Booster provides a `JwksUriTokenVerifier` class that you can use to configure a JWT token verifier. The `JwksUriTokenVerifier` constructor accepts the following parameters:

- `issuer`: The issuer of the tokens. This is a mandatory parameter. This is commonly found in the token payload under the `iss` key.
- `jwksUri`: The URL of the JSON Web Key Set (JWKS) that contains the public keys used to verify the tokens. This is a mandatory parameter. You can find more information about JWKS [here](https://auth0.com/docs/jwks).
- `rolesClaim`: The name of the claim that contains the user roles. This is an optional parameter. If not provided, the `roles` claim will be used. This is commonly found in the token payload under the `roles` key.

Here is an example of how to configure a `JwksUriTokenVerifier`:

```typescript title="src/config/config.ts"
import { Booster, JwksUriTokenVerifier } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'app-name'
  config.providerPackage = '@boostercloud/framework-provider-x'
  config.tokenVerifiers = [
      new JwksUriTokenVerifier(
        'https://my-auth0-tenant.auth0.com/', // Issuer
        'https://my-auth0-tenant.auth0.com/.well-known/jwks.json', // JWKS URL
        'role' // Roles claim
      ),
    ])
})
```

:::info JWK Verifier
One common way to validate JWT tokens is by using a issuer-provided well-known URI on which you can find their [JSON Web Key](https://datatracker.ietf.org/doc/html/rfc7517) sets (JWKS). If you use this method, you only need to provide the issuer's name, the JWKS URI and, if you're using role-based authentication, an optional `rolesClaim` option that sets the claim from which Booster will read the role names.
:::

### JWKS URI glossary

Here you can find a list of the most common authentication providers and their corresponding issuer, JWKS URI and roles claim:

:::caution
The issuer and JWKS URI may change depending on the region you're using. Please check the provider's documentation to find the correct values for your use case.

The following list is not exhaustive and the information may be deprecated. If you want to add a new provider, or update an existing one, please open a PR to have this content up to date.
:::

| Provider    | Issuer                                                      | JWKS URI                                                                          |
| ----------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Auth0       | `https://<your-tenant>.auth0.com/`                          | `https://<your-tenant>.auth0.com/.well-known/jwks.json`                           |
| AWS Cognito | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>/.well-known/jwks.json` |
| Okta        | `https://<your-tenant>.okta.com/oauth2/default`             | `https://<your-tenant>.okta.com/oauth2/default/v1/keys`                           |
| Google      | `https://accounts.google.com`                               | `https://www.googleapis.com/oauth2/v3/certs`                                      |
| Firebase    | `https://accounts.google.com`                               | `https://www.googleapis.com/oauth2/v3/certs`                                      |

## Public key based authentication

The `JwksUriTokenVerifier` class uses the public key of the issuer to verify the token signature. This means that the issuer must provide a JWKS URI that can be used to verify the token signature. This is the most common way to verify tokens, but it's not the only one. If you want to use a different method, you can implement your own `TokenVerifier` class.

This is useful when the token issuer doesn't provide a JWKS URI, when you're implementing your own authentication mechanism or you're issuing self-signed tokens.

```typescript title="src/config/config.ts"
import { Booster, PublicKeyTokenVerifier } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

function publicKeyResolver(): Promise<string> {
  // Your implementation here
}

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'app-name'
  config.providerPackage = '@boostercloud/framework-provider-x'
  config.tokenVerifiers = [
    new PublicKeyTokenVerifier(
      'issuer-name', // Issuer name
      publicKeyResolver(), // Promise that resolves to the public key string
      'custom:roles' // Name of the claim to read the roles from (if you're using role-based authorization)
    ),
  ]
})
```

:::info
Notice that the `publicKeyResolver` is a promise that resolves to a string, so it can be used to load the public key from a remote location too (i.e. get it from your KMS).
:::

:::tip
If you need to handle private keys in production, consider using a KMS [(Key Management System)](https://en.wikipedia.org/wiki/Key_management#Key_storage). These systems often provide API endpoints that let you encrypt/sign your JWT tokens without exposing the private keys. The public keys can be set in a `PublicKeyTokenVerifier` to automate verification.
:::

## Custom authentication

If you want to implement your own authentication mechanism, you can implement your own `TokenVerifier` class. This class must implement the following interface:

```typescript
interface TokenVerifier {
  /**
   * Verify asd deserialize a stringified token with this token verifier.
   * @param token The token to verify
   */
  verify(token: string): Promise<DecodedToken>

  /**
   * Build a valid `UserEnvelope` from a decoded token.
   * @param decodedToken The decoded token
   */
  toUserEnvelope(decodedToken: DecodedToken): UserEnvelope
}
```

Here is an example of how to implement a custom `TokenVerifier`:

```typescript title="src/config/config.ts"
import { Booster, TokenVerifier } from '@boostercloud/framework-core'
import { BoosterConfig, DecodedToken, TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'

class CustomTokenVerifier implements TokenVerifier {
  public async verify(token: string): Promise<DecodedToken> {
    // Your custom token verification logic here
  }

  public toUserEnvelope(decodedToken: DecodedToken): UserEnvelope {
    // Your custom logic to build a UserEnvelope from a decoded token here
  }
}

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'app-name'
  config.providerPackage = '@boostercloud/framework-provider-x'
  config.tokenVerifiers = [new CustomTokenVerifier()]
})
```

Some use cases for this could be to check that the token was generated specifically for your service by inspecting the `aud` claim, or check that the token has not been blacklisted or invalidated by your business logic (i.e. a user logs out before the token's expiration date and is included in an invalidated tokens list to make sure that an attacker that finds the token later can't use it to impersonate the legitimate owner).

### Extend existing token verifiers

If you only need to perform extra validations on top of one of the existing `TokenVerifier`s, you can extend one of the default implementations:

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

### Advanced authentication

If you need to do more advanced checks, you can implement the whole verification algorithm yourself. For example, if you're using non-standard or legacy tokens. Booster exposes for convenience many of the utility functions that it uses in the default `TokenVerifier` implementations:

| Function           | Description                                                                                                                                                                                        |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `getJwksClient`    | Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the `getKeyWithClient` function.                                                                           |
| `getKeyWithClient` | Initializes a function that can be used to get the public key from a JWKS URI with the signature required by the `verifyJWT` function. You can create a client using the `getJwksClient` function. |
| `verifyJWT`        | Verifies a JWT token using a key or key resolver function and returns a Booster UserEnvelope.                                                                                                      |

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
