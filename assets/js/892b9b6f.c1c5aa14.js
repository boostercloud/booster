"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[1173],{9513:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>s,default:()=>h,frontMatter:()=>r,metadata:()=>a,toc:()=>d});var i=n(5893),o=n(1151);const r={description:"Booster uses the OAuth 2.0 protocol to authenticate users. This section explains how to configure it."},s="Authentication",a={id:"security/authentication",title:"Authentication",description:"Booster uses the OAuth 2.0 protocol to authenticate users. This section explains how to configure it.",source:"@site/docs/04_security/01_authentication.md",sourceDirName:"04_security",slug:"/security/authentication",permalink:"/security/authentication",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/04_security/01_authentication.md",tags:[],version:"current",lastUpdatedBy:"\xc1ngel Guzm\xe1n Maeso",lastUpdatedAt:1726405461,formattedLastUpdatedAt:"Sep 15, 2024",sidebarPosition:1,frontMatter:{description:"Booster uses the OAuth 2.0 protocol to authenticate users. This section explains how to configure it."},sidebar:"docs",previous:{title:"Security",permalink:"/security/security"},next:{title:"Authorization",permalink:"/security/authorization"}},c={},d=[{value:"Configuring the authentication provider",id:"configuring-the-authentication-provider",level:2},{value:"JWKS URI glossary",id:"jwks-uri-glossary",level:3},{value:"Public key based authentication",id:"public-key-based-authentication",level:2},{value:"Custom authentication",id:"custom-authentication",level:2},{value:"Extend existing token verifiers",id:"extend-existing-token-verifiers",level:3},{value:"Advanced authentication",id:"advanced-authentication",level:3}];function l(e){const t={a:"a",admonition:"admonition",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",li:"li",p:"p",pre:"pre",table:"table",tbody:"tbody",td:"td",th:"th",thead:"thead",tr:"tr",ul:"ul",...(0,o.a)(),...e.components};return(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)(t.h1,{id:"authentication",children:"Authentication"}),"\n",(0,i.jsxs)(t.p,{children:["Booster uses the OAuth 2.0 protocol to authenticate users. That means that it uses tokens to identify users and authorize them. These tokens are called ",(0,i.jsx)(t.em,{children:"access tokens"})," and are issued by an ",(0,i.jsx)(t.em,{children:"authentication provider"}),". The most common authentication provider is ",(0,i.jsx)(t.a,{href:"https://auth0.com/",children:"Auth0"}),", but you can use any other provider that supports OAuth 2.0."]}),"\n",(0,i.jsx)(t.h2,{id:"configuring-the-authentication-provider",children:"Configuring the authentication provider"}),"\n",(0,i.jsxs)(t.p,{children:["The first step to configure authentication in Booster is to configure the authentication provider. The provider must support OAuth 2.0 and must be able to issue ",(0,i.jsx)(t.em,{children:"access tokens"}),". In order to validate incoming tokens and make sure that user requests come from trustable origins, you need to provide one or more ",(0,i.jsx)(t.code,{children:"TokenVerifier"})," instances at config time for each of your environments."]}),"\n",(0,i.jsxs)(t.p,{children:["The ",(0,i.jsx)(t.code,{children:"TokenVerifier"})," class is a simple interface that you can implement to define your own token verifiers. Booster provides a ",(0,i.jsx)(t.code,{children:"JwksUriTokenVerifier"})," class that you can use to configure a JWT token verifier. The ",(0,i.jsx)(t.code,{children:"JwksUriTokenVerifier"})," constructor accepts the following parameters:"]}),"\n",(0,i.jsxs)(t.ul,{children:["\n",(0,i.jsxs)(t.li,{children:[(0,i.jsx)(t.code,{children:"issuer"}),": The issuer of the tokens. This is a mandatory parameter. This is commonly found in the token payload under the ",(0,i.jsx)(t.code,{children:"iss"})," key."]}),"\n",(0,i.jsxs)(t.li,{children:[(0,i.jsx)(t.code,{children:"jwksUri"}),": The URL of the JSON Web Key Set (JWKS) that contains the public keys used to verify the tokens. This is a mandatory parameter. You can find more information about JWKS ",(0,i.jsx)(t.a,{href:"https://auth0.com/docs/jwks",children:"here"}),"."]}),"\n",(0,i.jsxs)(t.li,{children:[(0,i.jsx)(t.code,{children:"rolesClaim"}),": The name of the claim that contains the user roles. This is an optional parameter. If not provided, the ",(0,i.jsx)(t.code,{children:"roles"})," claim will be used. This is commonly found in the token payload under the ",(0,i.jsx)(t.code,{children:"roles"})," key."]}),"\n"]}),"\n",(0,i.jsxs)(t.p,{children:["Here is an example of how to configure a ",(0,i.jsx)(t.code,{children:"JwksUriTokenVerifier"}),":"]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/config.ts"',children:"import { Booster, JwksUriTokenVerifier } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n\nBooster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'app-name'\n  config.providerPackage = '@boostercloud/framework-provider-x'\n  config.tokenVerifiers = [\n      new JwksUriTokenVerifier(\n        'https://my-auth0-tenant.auth0.com/', // Issuer\n        'https://my-auth0-tenant.auth0.com/.well-known/jwks.json', // JWKS URL\n        'role' // Roles claim\n      ),\n    ])\n})\n"})}),"\n",(0,i.jsx)(t.admonition,{title:"JWK Verifier",type:"info",children:(0,i.jsxs)(t.p,{children:["One common way to validate JWT tokens is by using a issuer-provided well-known URI on which you can find their ",(0,i.jsx)(t.a,{href:"https://datatracker.ietf.org/doc/html/rfc7517",children:"JSON Web Key"})," sets (JWKS). If you use this method, you only need to provide the issuer's name, the JWKS URI and, if you're using role-based authentication, an optional ",(0,i.jsx)(t.code,{children:"rolesClaim"})," option that sets the claim from which Booster will read the role names."]})}),"\n",(0,i.jsx)(t.h3,{id:"jwks-uri-glossary",children:"JWKS URI glossary"}),"\n",(0,i.jsx)(t.p,{children:"Here you can find a list of the most common authentication providers and their corresponding issuer, JWKS URI and roles claim:"}),"\n",(0,i.jsxs)(t.admonition,{type:"caution",children:[(0,i.jsx)(t.p,{children:"The issuer and JWKS URI may change depending on the region you're using. Please check the provider's documentation to find the correct values for your use case."}),(0,i.jsx)(t.p,{children:"The following list is not exhaustive and the information may be deprecated. If you want to add a new provider, or update an existing one, please open a PR to have this content up to date."})]}),"\n",(0,i.jsxs)(t.table,{children:[(0,i.jsx)(t.thead,{children:(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.th,{children:"Provider"}),(0,i.jsx)(t.th,{children:"Issuer"}),(0,i.jsx)(t.th,{children:"JWKS URI"})]})}),(0,i.jsxs)(t.tbody,{children:[(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:"Auth0"}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://<your-tenant>.auth0.com/"})}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://<your-tenant>.auth0.com/.well-known/jwks.json"})})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:"AWS Cognito"}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://cognito-idp.<region>.amazonaws.com/<user-pool-id>"})}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://cognito-idp.<region>.amazonaws.com/<user-pool-id>/.well-known/jwks.json"})})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:"Okta"}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://<your-tenant>.okta.com/oauth2/default"})}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://<your-tenant>.okta.com/oauth2/default/v1/keys"})})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:"Google"}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://accounts.google.com"})}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://www.googleapis.com/oauth2/v3/certs"})})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:"Firebase"}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://accounts.google.com"})}),(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"https://www.googleapis.com/oauth2/v3/certs"})})]})]})]}),"\n",(0,i.jsx)(t.h2,{id:"public-key-based-authentication",children:"Public key based authentication"}),"\n",(0,i.jsxs)(t.p,{children:["The ",(0,i.jsx)(t.code,{children:"PublicKeyTokenVerifier"})," class uses the public key of the issuer to verify the token signature. This means that the issuer must provide a JWKS URI that can be used to verify the token signature. This is the most common way to verify tokens, but it's not the only one. If you want to use a different method, you can implement your own ",(0,i.jsx)(t.code,{children:"TokenVerifier"})," class."]}),"\n",(0,i.jsx)(t.p,{children:"This is useful when the token issuer doesn't provide a JWKS URI, when you're implementing your own authentication mechanism or you're issuing self-signed tokens."}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/config.ts"',children:"import { Booster, PublicKeyTokenVerifier } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n\nfunction publicKeyResolver(): Promise<string> {\n  // Your implementation here\n}\n\nBooster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'app-name'\n  config.providerPackage = '@boostercloud/framework-provider-x'\n  config.tokenVerifiers = [\n    new PublicKeyTokenVerifier(\n      'issuer-name', // Issuer name\n      publicKeyResolver(), // Promise that resolves to the public key string\n      'custom:roles' // Name of the claim to read the roles from (if you're using role-based authorization)\n    ),\n  ]\n})\n"})}),"\n",(0,i.jsx)(t.admonition,{type:"info",children:(0,i.jsxs)(t.p,{children:["Notice that the ",(0,i.jsx)(t.code,{children:"publicKeyResolver"})," is a promise that resolves to a string, so it can be used to load the public key from a remote location too (i.e. get it from your KMS)."]})}),"\n",(0,i.jsx)(t.admonition,{type:"tip",children:(0,i.jsxs)(t.p,{children:["If you need to handle private keys in production, consider using a KMS ",(0,i.jsx)(t.a,{href:"https://en.wikipedia.org/wiki/Key_management#Key_storage",children:"(Key Management System)"}),". These systems often provide API endpoints that let you encrypt/sign your JWT tokens without exposing the private keys. The public keys can be set in a ",(0,i.jsx)(t.code,{children:"PublicKeyTokenVerifier"})," to automate verification."]})}),"\n",(0,i.jsx)(t.h2,{id:"custom-authentication",children:"Custom authentication"}),"\n",(0,i.jsxs)(t.p,{children:["If you want to implement your own authentication mechanism, you can implement your own ",(0,i.jsx)(t.code,{children:"TokenVerifier"})," class. This class must implement the following interface:"]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",children:"interface TokenVerifier {\n  /**\n   * Verify asd deserialize a stringified token with this token verifier.\n   * @param token The token to verify\n   */\n  verify(token: string): Promise<DecodedToken>\n\n  /**\n   * Build a valid `UserEnvelope` from a decoded token.\n   * @param decodedToken The decoded token\n   */\n  toUserEnvelope(decodedToken: DecodedToken): UserEnvelope\n}\n"})}),"\n",(0,i.jsxs)(t.p,{children:["Here is an example of how to implement a custom ",(0,i.jsx)(t.code,{children:"TokenVerifier"}),":"]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/config.ts"',children:"import { Booster, TokenVerifier } from '@boostercloud/framework-core'\nimport { BoosterConfig, DecodedToken, TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'\n\nclass CustomTokenVerifier implements TokenVerifier {\n  public async verify(token: string): Promise<DecodedToken> {\n    // Your custom token verification logic here\n  }\n\n  public toUserEnvelope(decodedToken: DecodedToken): UserEnvelope {\n    // Your custom logic to build a UserEnvelope from a decoded token here\n  }\n}\n\nBooster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'app-name'\n  config.providerPackage = '@boostercloud/framework-provider-x'\n  config.tokenVerifiers = [new CustomTokenVerifier()]\n})\n"})}),"\n",(0,i.jsxs)(t.p,{children:["Some use cases for this could be to check that the token was generated specifically for your service by inspecting the ",(0,i.jsx)(t.code,{children:"aud"})," claim, or check that the token has not been blacklisted or invalidated by your business logic (i.e. a user logs out before the token's expiration date and is included in an invalidated tokens list to make sure that an attacker that finds the token later can't use it to impersonate the legitimate owner)."]}),"\n",(0,i.jsx)(t.h3,{id:"extend-existing-token-verifiers",children:"Extend existing token verifiers"}),"\n",(0,i.jsxs)(t.p,{children:["If you only need to perform extra validations on top of one of the existing ",(0,i.jsx)(t.code,{children:"TokenVerifier"}),"s, you can extend one of the default implementations:"]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",children:"export class CustomValidator extends PrivateKeyValidator {\n  public async verify(token: string): Promise<UserEnvelope> {\n    // Call to the PrivateKeyValidator verify method to check the signature\n    const userEnvelope = await super.verify(token)\n\n    // Do my extra validations here. Throwing an error will reject the token\n    await myExtraValidations(userEnvelope.claims, token)\n\n    return userEnvelope\n  }\n}\n"})}),"\n",(0,i.jsx)(t.h3,{id:"advanced-authentication",children:"Advanced authentication"}),"\n",(0,i.jsxs)(t.p,{children:["If you need to do more advanced checks, you can implement the whole verification algorithm yourself. For example, if you're using non-standard or legacy tokens. Booster exposes for convenience many of the utility functions that it uses in the default ",(0,i.jsx)(t.code,{children:"TokenVerifier"})," implementations:"]}),"\n",(0,i.jsxs)(t.table,{children:[(0,i.jsx)(t.thead,{children:(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.th,{children:"Function"}),(0,i.jsx)(t.th,{children:"Description"})]})}),(0,i.jsxs)(t.tbody,{children:[(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"getJwksClient"})}),(0,i.jsxs)(t.td,{children:["Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the ",(0,i.jsx)(t.code,{children:"getKeyWithClient"})," function."]})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"getKeyWithClient"})}),(0,i.jsxs)(t.td,{children:["Initializes a function that can be used to get the public key from a JWKS URI with the signature required by the ",(0,i.jsx)(t.code,{children:"verifyJWT"})," function. You can create a client using the ",(0,i.jsx)(t.code,{children:"getJwksClient"})," function."]})]}),(0,i.jsxs)(t.tr,{children:[(0,i.jsx)(t.td,{children:(0,i.jsx)(t.code,{children:"verifyJWT"})}),(0,i.jsx)(t.td,{children:"Verifies a JWT token using a key or key resolver function and returns a Booster UserEnvelope."})]})]})]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",children:"/**\n * Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the\n * `getKeyWithClient` function.\n */\nexport function getJwksClient(jwksUri: string) {\n  ...\n}\n\n/**\n * Initializes a function that can be used to get the public key from a JWKS URI with the signature\n * required by the `verifyJWT` function. You can create a client using the `getJwksClient` function.\n */\nexport function getKeyWithClient(\n  client: jwksRSA.JwksClient,\n  header: jwt.JwtHeader,\n  callback: jwt.SigningKeyCallback\n): void {\n  ...\n}\n\n/**\n * Verifies a JWT token using a key or key resolver function and returns a Booster UserEnvelope.\n */\nexport async function verifyJWT(\n  token: string,\n  issuer: string,\n  key: jwt.Secret | jwt.GetPublicKeyOrSecret,\n  rolesClaim?: string\n): Promise<UserEnvelope> {\n ...\n}\n"})})]})}function h(e={}){const{wrapper:t}={...(0,o.a)(),...e.components};return t?(0,i.jsx)(t,{...e,children:(0,i.jsx)(l,{...e})}):l(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>a,a:()=>s});var i=n(7294);const o={},r=i.createContext(o);function s(e){const t=i.useContext(r);return i.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function a(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:s(e.components),i.createElement(r.Provider,{value:t},e.children)}}}]);