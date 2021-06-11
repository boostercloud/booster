# Features

## Local Provider

All Booster projects come with a local development environment configured by default, so you can test your app before deploying it to the cloud.

You can see the configured local environment in your `src/config/config.ts` file:

```typescript
Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-local'
})
```

Note how, contrary to the rest of the environments, the configuration of the local environment is using the `config.providerPackage` field,
and not the `config.provider`.

This is a special field that allows deferring the loading of the
provider to later, so you don't get dependencies errors when you
upload your app to production, given that the local provider lives in
the `devDependencies` section of your `package.json`

In order to start your application using the local provider, use the following command:

```bash
boost start -e local
```

Where `local` is one of your defined environments with the `Booster.configure` call.

## Authentication and Authorization

First of all, you need to know that the authorization in Booster is done through roles. Every Command and ReadModel has an authorize policy that tells Booster who can execute or access it. It consists of one of the following two values:

- `'all'`: Meaning that the command is public: any user, both authenticated and anonymous, can execute it.
- An array of authorized roles `[Role1, Role2, ...]`: This means that only those authenticated users that
  have any of the roles listed there are authorized to execute the command

For example, the following command can be executed by anyone:

```typescript
@Command({
  authorize: 'all',
})
export class CreateComment {
  ...
}
```

While this one can be executed by authenticated users that have the role `Admin` or `User`:

```typescript
@Command({
  authorize: [Admin, User],
})
export class UpdateUser {
  ...
}
```

Optionally, you can also add authorization to entities to control who can read its events. To do so, pass a configuration object to the @Entity annotation with the authorized roles (or `'all'` for everyone) in the `authorizeReadEvents` field. For example:

```typescript
// cart.ts (entity)
@Entity({
  authorizeReadEvents: 'all',
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

By default, a Booster application has no roles defined, so the only allowed value you can use in the `authorize` (or `authorizeReadEvents`) policy is `'all'` (good for public APIs).
If you want to add user authorization, you first need to create the roles that are suitable for your application.

Roles are classes annotated with the `@Role` decorator, where you can specify some attributes. We recommend that you define your roles in the file `src/roles.ts` or, if you have too many roles, put them in several files under the `src/roles` folder.

> [!NOTE] There is no `Admin` user by default. In order to register one you need to specify a sign-up method on `src/roles.ts`.

In the following example we define `Admin`, `User`, `SuperUser` and `SuperUserWithoutConfirmation` roles. They all contain an `auth` attribute which contains a `signUpMethods` and `skipConfirmation` attributes.

```typescript
// src/roles.ts

@Role({
  auth: {
    // Do not specify (or use an empty array) if you don't want to allow sign-ups
    signUpMethods: [],
  },
})
export class Admin {}

@Role({
  auth: {
    // Do not specify (or use an empty array) if you don't want to allow sign-ups
    signUpMethods: ['email'],
  },
})
export class User {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    skipConfirmation: false,
  },
})
export class SuperUser {}

@Role({
  auth: {
    signUpMethods: ['email', 'phone'],
    skipConfirmation: true,
  },
})
export class SuperUserWithoutConfirmation {}
```

When `signUpMethods` is empty (`Admin` role) or is not specified, a user can't use this role to sign up.
`signUpMethods` is an array with limited possible values: `email`, `phone` or a combination of both.
Users with the `User` role will only be able to sign up with their emails, whereas the ones with the `SuperUser` role will be able to sign up with either their email or their phone number.

When `skipConfirmation` is false or not specified, a confirmation is required for the chosen sign up method.
Users that sign up with their emails will receive a confirmation link in their inbox. They just need to click it to confirm their registration.
Users that sign up with their phones will receive a confirmation code as an SMS message. That code needs to be sent back using the confirmation endpoint.
If `skipConfirmation` is set to true, users can sign in without confirmation after signing up.

## Autentication Rocket

Now, with the roles defined, your Booster application is ready to use the [AWS Authorization Rocket](https://github.com/boostercloud/rocket-auth-aws-infrastructure), which provides authentication and authorization integration in your application. Check out its documentation to know how you can configure it and how the user can get their access tokens.

Once a user has an access token, it can be included in any request made to your Booster application as a Bearer token. It will be used to get the user information and authorize them to access protected resources.

To learn how to include the access token in your requests, check the section [Authorizing operations](#authorizing-operations).

## Custom Authentication

Booster provides a **JWT authorization mode** to authorize all incoming Booster requests using the server you decide. Your authentication server will provide JWT tokens that you can use with Booster. Your application will decode your token and verify its validity with your server, and then, the required roles will be matched with the claims contained in the token.

In that way, you can use different authentication providers, like Auth0, Firebase, Cognito, or create your own, without the need of a specific rocket implementation.

> [!NOTE] The JWT authorization mode does not make use of the `signUpMethods` and `signUpConfirmation` attributes of your roles configuration. This configuration depends on the authentication server you use.

### JWT Configuration

In order to use the JWT authorization you will need to set a `tokenVerifier` property which contains the following properties:

- jwksUri: Public uri with the public keys the auth server used to sign in the JWT tokens, commonly known as a key sets.
- issuer: Identifies the principal that issued the JWT tokens.
- rolesClaim: Field where provider contains the token. As an example Cognito uses `cognito:groups`.

Auth sample configuration:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'demoapp'
  config.provider = AWS.Provider
  config.tokenVerifier = {
    jwksUri: 'https://demoapp.firebase.com/.well-known/jwks.json',
    issuer: 'https://securetoken.google.com/demoapp',
    rolesClaim: 'firebase:groups',
  }
})
```

In addition to that, you will need to configure your JWT tokens to include the custom claims required in your Booster app, i.e. if your command is declared in this way:

```typescript
@Command({
  authorize: [Admin, User],
})
export class UpdateUser {
  ...
}
```

Your token should include a property specified in `rolesClaim` with the value `Admin` or `User`. Here is an example of a Firebase token:

```json
{
  "firebase:groups": "User",
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

Once you have the token with the auth provider of choice, simply pass it in the requests through the header:

```http request
Authorization: Bearer <your JWT token>
```

## GraphQL API

This is the main API of your application, as it allows you to:

- _Modify_ data by **sending commands**.
- _Read_ data by **querying read models**.
- _Receive data in real time_ by **subscribing to read models**.

All this is done through [GraphQL](https://graphql.org/), a query language for APIs that has useful advantages over simple REST APIs.

If you are not familiar with GraphQL, then, first of all, don't worry!
_Using_ a GraphQL API is simple and straightforward.
_Implementing it_ on the server side is usually the hard part, as you need to define your schema, operations, resolvers, etc.
Luckily, you can forget about that because Booster does all the work for you!

The GraphQL API is fully **auto-generated** based on your _commands_ and _read models_.

**Note:** To get the full potential of the GraphQL API, it is recommended not to use `interface` types in any command or read model attributes. Use `class` types instead. This will allow you to perform complex graphQL filters, including over nested attributes. There's an example below:

```typescript
// My type
export class ItemWithQuantity {
  // Use "class", not "interface"
  public constructor(sku: string, quantity: number) {}
}
```

```typescript
// The read-model file
import { ItemWithQuantity } from "./types";
@ReadModel({
  authorize: 'all'
})
export class CartReadModel {
  public constructor(
          readonly id: UUID,
          item: ItemWithQuantity // As ItemWithQuantity is a class, you will be able to query over nested attributes like item `quantity`
  ) {}
```

### Relationship between GraphQL operations and commands and read models

GraphQL defines three kinds of operations that you can use: _mutations_, _queries_, and _subscriptions_.

The names are pretty meaningful, but we can say that you use a `mutation` when you want to change data, a `query` when you want to get
data on-demand, and a `subscription` when you want to receive data at the moment it is updated.

Knowing this, you can infer the relationship between those operations and your Booster components:

- You _send_ a **command** using a **mutation**.
- You _read_ a **read model** using a **query**.
- You _subscribe_ to a **read model** using a **subscription**.

### How to send GraphQL request

GraphQL uses two existing protocols:

- _HTTP_ for `mutation` and `query` operations.
- _WebSocket_ for `subscription` operations.

The reason for the WebSocket protocol is that, in order for subscriptions to work, there must be a way for the server to send data to clients when it is changed. HTTP doesn't allow that, as it is the client the one which always initiates the request.

So you should use the **graphqlURL** to send GraphQL queries and mutations, and the **websocketURL** to send subscriptions. You can see both URLs after deploying your application.

Therefore:

- To send a GraphQL mutation/query, you send an HTTP request to _"&lt;graphqlURL&gt;"_, with _method POST_, and a _JSON-encoded body_ with the mutation/query details.
- To send a GraphQL subscription, you first connect to the _"&lt;websocketURL&gt;"_, and then send a _JSON-encoded message_ with the subscription details, _following [the "GraphQL over WebSocket" protocol](#the-graphql-over-websocket-protocol)_.

> [!NOTE] you can also **send queries and mutations through the WebSocket** if that's convenient to you. See ["The GraphQL over WebSocket protocol"](#the-graphql-over-websocket-protocol) to know more.

While it is OK to know how to manually send GraphQL request, you normally don't need to deal with this low-level details, especially with the WebSocket stuff.

To have a great developer experience, we **strongly recommend** to use a GraphQL client for your platform of choice. Here are some great ones:

- **[Altair](https://altair.sirmuel.design/)**: Ideal for testing sending manual requests, getting the schema, etc.
- **Apollo clients**: These are the "go-to" SDKs to interact with a GraphQL API from your clients. It is very likely that there is a version for your client programming language. Check the ["Using Apollo Client"](#using-apollo-client) section to know more about this.

### Get GraphQL schema from deployed application

After deploying your application with the command `boost deploy -e development`, you can get your GraphQL schema by using a tool like **[Altair](https://altair.sirmuel.design/)**. The previous command displays multiple endpoints, one of them is **graphqlURL**, which has the following pattern:

`https://<base_url>/<environment>/graphql`

By entering this URL in Altair, the schema can be displayed as shown in the screenshot (You need to click on the Docs button in the URL bar). You can
check the available Queries and Mutations by clicking on their name:

![Altair queries](../img/altair-queries.png)
![Altair mutations](../img/altair-mutations.png)

### Sending commands

As mentioned in the previous section, we need to use a "mutation" to send a command. The structure of a mutation (the body of the request) is the following:

```graphql
mutation {
  command_name(input: {
    input_field_list
  })
}
```

Where:

- _**command_name**_ is the name of the class corresponding to the command you want to send
- _**input_field_list**_ is a list of pairs in the form of `fieldName: fieldValue` containing the data of your command. The field names correspond to the names of the properties you defined in the command class.

In the following example we send a command named "ChangeCart" that will add/remove an item to/from a shopping cart. The command requires the ID of the cart (`cartId`), the item identifier (`sku`) and the quantity of units we are adding/removing (`quantity`).

```
URL: "<graphqlURL>"
```

```graphql
mutation {
  ChangeCart(input: { cartId: "demo", sku: "ABC_01", quantity: 2 })
}
```

In case we are not using any GraphQL client, this would be the equivalent bare HTTP request:

```
URL: "<graphqlURL>"
METHOD: "POST"
```

```json
{
  "query": "mutation { ChangeCart(input: { cartId: \"demo\" sku: \"ABC_01\" quantity: 2 }) }"
}
```

And this would be the response:

```json
{
  "data": {
    "ChangeCart": true
  }
}
```

> [!NOTE] Remember to set the proper **access token** for secured commands, check ["Authorizing operations"](#authorizing-operations).

### Reading read models

To read a specific read model, we need to use a "query" operation. The structure of the "query" (the body
of the request) is the following:

```graphql
query {
  read_model_name(id: "<id of the read model>") {
    selection_field_list
  }
}
```

Where:

- _read_model_name_ is the name of the class corresponding to the read model you want to retrieve.
- _&lt;id of the read model&gt;_ is the ID of the specific read model instance you are interested in.
- _selection_field_list_ is a list with the names of the specific read model fields you want to get as response.

In the following example we send a query to read a read model named `CartReadModel` whose ID is `demo`. We get back its `id` and the list of cart `items` as response.

```
URL: "<graphqlURL>"
```

```graphql
query {
  CartReadModel(id: "demo") {
    id
    items
  }
}
```

In case we are not using any GraphQL client, this would be the equivalent bare HTTP request:

```
URL: "<graphqlURL>"
METHOD: "POST"
```

```json
{
  "query": "query { CartReadModel(id: \"demo\") { id items } }"
}
```

And we would get the following as response:

```json
{
  "data": {
    "CartReadModel": {
      "id": "demo",
      "items": [
        {
          "sku": "ABC_01",
          "quantity": 2
        }
      ]
    }
  }
}
```

> [!NOTE] Remember to set the proper **access token** for secured read models, check ["Authorizing operations"](#authorizing-operations).

### Subscribing to read models

To subscribe to a specific read model, we need to use a subscription operation, and it must be _sent through the **websocketURL**_ using the [_GraphQL over WebSocket_ protocol](#the-graphql-over-websocket-protocol).

Doing this process manually is a bit cumbersome. _You will probably never need to do this_, as GraphQL clients like [Apollo](#using-apollo-client) abstract this process away. However, we will explain how to do it for learning purposes.

Before sending any subscription, you need to _connect_ to the WebSocket to open the two-way communication channel. This connection is done differently depending on the client/library you use to manage web sockets. In this section, we will show examples using the [`wscat`](https://github.com/websockets/wscat) command line program. You can also use the online tool [Altair](https://altair.sirmuel.design/)

Once you have connected successfully, you can use this channel to:

- Send the subscription messages.
- Listen for messages sent by the server with data corresponding to your active subscriptions.

The structure of the "subscription" (the body of the message) is exactly the same as the "query" operation:

```graphql
subscription {
  read_model_name(id: "<id of the read model>") {
    selection_field_list
  }
}
```

Where:

- _read_model_name_ is the name of the class corresponding to the read model you want to subscribe to.
- _&lt;id of the read model&gt;_ is the ID of the specific read model instance you are interested in.
- _selection_field_list_ is a list with the names of the specific read model fields you want to get when data is sent back to you.

In the following examples we use [`wscat`](https://github.com/websockets/wscat) to connect to the web socket. After that, we send the required messages to conform the [_GraphQL over WebSocket_ protocol](#the-graphql-over-websocket-protocol), including the subscription operation to the read model `CartReadModel` with ID `demo`.

1. Connect to the web socket:

```sh
 wscat -c <websocketURL> -s graphql-ws
```

> [!NOTE] You should specify the `graphql-ws` subprotocol when connecting with your client via the `Sec-WebSocket-Protocol` header (in this case, `wscat` does that when you use the `-s` option).

Now we can start sending messages just by writing them and hitting the <kbd>Enter</kbd> key.

2. Initiate the protocol connection :

```json
{ "type": "connection_init" }
```

In case you want to authorize the connection, you need to send the authorization token in the `payload.Authorization` field:

```json
{ "type": "connection_init", "payload": { "Authorization": "<your token>" } }
```

3. Send a message with the subscription. We need to provide an ID for the operation. When the server sends us data back, it will include this same ID so that we know which subscription the received data belongs to (again, this is just for learning, [GraphQL clients](#using-apollo-client) manages this for you)

```json
{ "id": "1", "type": "start", "payload": { "query": "subscription { CartReadModel(id:\"demo\") { id items } }" } }
```

After a successful subscription, you won't receive anything in return. Now, every time the read model you subscribed to is modified, a new incoming message will appear in the socket with the updated version of the read model. This message will have exactly the same format as if you were done a query with the same parameters.

Following with the previous example, we now send a command (using a mutation operation) that adds a new item with sku "ABC_02" to the `CartReadModel`. After it has been added, we receive the updated version of the read model through the socket.

1. Send the following command (this time using an HTTP request):

```
URL: "<graphqlURL>"
```

```graphql
mutation {
  ChangeCart(input: { cartId: "demo", sku: "ABC_02", quantity: 3 })
}
```

2. The following message (after formatting it) appears through the socket connection we had opened:

```json
{
  "id": "1",
  "type": "data",
  "payload": {
    "data": {
      "CartReadModel": {
        "id": "demo",
        "items": [
          {
            "sku": "ABC_01",
            "quantity": 2
          },
          {
            "sku": "ABC_02",
            "quantity": 3
          }
        ]
      }
    }
  }
}
```

> [!NOTE] Remember that, in case you want to subscribe to a read model that is restricted to a specific set of roles, you must send the **access token** retrieved upon sign-in. Check ["Authorizing operations"](#authorizing-operations) to know how to do this.

### Reading events

You can also fetch events directly if you need. To do so, there are two kind of queries that have the following structure:

```graphql
query {
  eventsByEntity(entity: <name of entity>, entityID: "<id of the entity>") {
    selection_field_list
  }
}

query {
  eventsByType(type: <name of event>) {
    selection_field_list
  }
}
```

Where:

- _&lt;name of your entity&gt;_ is the name of the class corresponding to the entity whose events you want to retrieve.
- _&lt;id of the entity&gt;_ is the ID of the specific entity instance whose events you are interested in. **This is optional**
- _&lt;name of event&gt;_ is the name of the class corresponding to the event type whose instances you want to retrieve.
- _selection_field_list_ is a list with the names of the specific fields you want to get as response. See the response example below to know more.

#### Examples

```
  URL: "<graphqlURL>"
```

**A) Read all events associated with a specific instance (a specific ID) of the entity Cart**

```graphql
query {
  eventsByEntity(entity: Cart, entityID: "ABC123") {
    type
    entity
    entityID
    requestID
    createdAt
    value
  }
}
```

**B) Read all events associated with any instance of the entity Cart**

```graphql
query {
  eventsByEntity(entity: Cart) {
    type
    entity
    entityID
    requestID
    createdAt
    value
  }
}
```

For these cases, you would get an array of event _envelopes_ as a response. This means that you get some metadata related to the event along with the event content, which can be found inside the `"value"` field.

The response look like this:

```json
{
  "data": {
    "eventsByEntity": [
      {
        "type": "CartItemChanged",
        "entity": "Cart",
        "entityID": "ABC123",
        "requestID": "7a9cc6a7-7c7f-4ef0-aef1-b226ae4d94fa",
        "createdAt": "2021-05-12T08:41:13.792Z",
        "value": {
          "productId": "73f7818c-f83e-4482-be49-339c004b6fdf",
          "cartId": "ABC123",
          "quantity": 2
        }
      }
    ]
  }
}
```

**C) Read events of a specific type**

```graphql
query {
  eventsByType(type: CartItemChanged) {
    type
    entity
    entityID
    requestID
    createdAt
    value
  }
}
```

The response would have the same structure as seen in the previous examples. The only difference is that this time you will get only the events with the type you have specified ("CartItemChanged")

#### Time filters

Optionally, for any of the previous queries, you can include a `from` and/or `to` time filters to get only those events that happened inside that time range. You must use a string with a time in ISO format with any precision you like, for example:

- `from:"2021"` : Events created on 2021 year or up.
- `from:"2021-02-12" to:"2021-02-13"` : Events created during February 12th.
- `from:"2021-03-16T16:16:25.178"` : Events created at that date and time, using millisecond precision, or later.

#### Time filters examples

**A) Cart events from February 23rd to July 20th, 2021**

```graphql
query {
  eventsByEntity(entity: Cart, from: "2021-02-23", to: "2021-07-20") {
    type
    entity
    entityID
    requestID
    createdAt
    value
  }
}
```

**B) CartItemChanged events from February 25th to February 28th, 2021**

```graphql
query {
  eventsByType(type: CartItemChanged, from: "2021-02-25", to: "2021-02-28") {
    type
    entity
    entityID
    requestID
    createdAt
    value
  }
}
```

#### Known limitations

- Subscriptions don't work for the events API yet
- You can only query events, but not write them through this API. Use a command for that.

### Using Apollo Client

One of the best clients to connect to a GraphQL API is the [Apollo](https://www.apollographql.com/) client. There will probably be a version for your client technology of choice. These are the main ones:

- [For Javascript/Typescript](https://www.apollographql.com/docs/react/) ([Github](https://github.com/apollographql/apollo-client))
- [For iOS](https://www.apollographql.com/docs/ios/) ([Github)](https://github.com/apollographql/apollo-ios))
- [For Java/Kotlin/Android](https://www.apollographql.com/docs/android/) ([Github](https://github.com/apollographql/apollo-android))

We recommend referring to the documentation of those clients to know how to use them. Here is an example of how to fully instantiate the Javascript client so that it works for queries, mutations and subscriptions:

```typescript
import { split, HttpLink } from '@apollo/client'
import { getMainDefinition } from '@apollo/client/utilities'
import { WebSocketLink } from '@apollo/client/link/ws'
import { ApolloClient, InMemoryCache } from '@apollo/client'
import { SubscriptionClient } from 'subscriptions-transport-ws'

// Helper function that checks if a GraphQL operation is a subscription or not
function isSubscriptionOperation({ query }) {
  const definition = getMainDefinition(query)
  return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
}

// Create an HTTP link for sending queries and mutations
const httpLink = new HttpLink({
  uri: '<graphqlURL>',
})

// Create a SusbscriptionClient and a WebSocket link for sending subscriptions
const subscriptionClient = new SubscriptionClient('<websocketURL>', {
  reconnect: true,
})
const wsLink = new WebSocketLink(subscriptionClient)

// Combine both links so that depending on the operation, it uses one or another
const splitLink = split(isSubscriptionOperation, wsLink, httpLink)

// Finally, create the client using the link created above
const client = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(),
})
```

Now, we can send queries, mutations and subscriptions using the `client` instance:

```typescript
import gql from 'graphql-tag'

// Query the CartReadModel
const readModelData = await client.query({
  variables: {
    cartID: 'demo',
  },
  query: gql`
    query QueryCart($cartID: ID!) {
      CartReadModel(id: $cartID) {
        id
        items
      }
    }
  `,
})

// Send a command (mutation)
const commandResult = await client.mutate({
  variables: {
    cartID: 'demo',
    sku: 'ABC_02',
  },
  mutation: gql`
    mutation AddOneItemToCart($cartID: ID!, $sku: string!) {
      ChangeCart(input: { cartId: $cartID, sku: $sku, quantity: 1 })
    }
  `,
})

// Subscribe to changes in the CartReadModel
const subscriptionOperation = client.subscribe({
  variables: {
    cartID: 'demo',
  },
  query: gql`
    subscription SubscribeToCart($cartID: ID!) {
      CartReadModel(id: $cartID) {
        id
        cartItems
      }
    }
  `,
})

subscriptionOperation.subscribe({
  next: (cartReadModel) => {
    // This function is called everytime the CartReadModel with ID="demo" is changed
    // Parameter "cartReadModel" contains the latest version of the cart
  },
})
```

### Authorizing operations

When you have a command or read model whose access is authorized to users with a specific set of roles (see [Authentication and Authorization](#authentication-and-authorization)), you need to use an authorization token to send queries, mutations or subscriptions to that command or read model.

You can use Authentication Rocket to authorize operations, see its [documentation](https://github.com/boostercloud/rocket-auth-aws-infrastructure) and, more especifically, the [Sign in](https://github.com/boostercloud/rocket-auth-aws-infrastructure#sign-in) section to know how to get a token. Once you have a token, the way to send it varies depending on the protocol you are using to send GraphQL operations:

- For **HTTP**, you need to send the HTTP header `Authorization` with the token, making sure you prefix it with `Bearer` (the kind of token Booster uses). For example:

```http request
Authorization: Bearer <your token>
```

- For **WebSocket**, you need to adhere to the [GraphQL over WebSocket protocol](#the-graphql-over-websocket-protocol) to send authorization data. The way to do that is by sending the token in the payload of the first message you send when initializing the connection (see [Subscribing to read models](#subscribing-to-read-models)). For example:

```json
{ "type": "connection_init", "payload": { "Authorization": "<your token>" } }
```

You normally won't be sending tokens in such a low-level way. GraphQL clients have easier ways to send these tokens. See [Sending tokens with Apollo client](#sending-tokens-with-apollo-clients)

#### Sending tokens with Apollo clients

We recommend going to the specific documentation of the specific Apollo client you are using to know how to send tokens. However, the basics of this guide remains the same. Here is an example of how you would configure the Javascript/Typescript Apollo client to send the authorization token. The example is exactly the same as the one shown in the [Using Apollo clients](#using-apollo-client) section, but with the changes needed to send the token. Notice that `<AuthApiEndpoint>` and `<idToken>` are obtained from the [Authentication Rocket](https://github.com/boostercloud/rocket-auth-aws-infrastructure).

```typescript
import { split, HttpLink, ApolloLink } from '@apollo/client'
import { getMainDefinition } from '@apollo/client/utilities'
import { WebSocketLink } from '@apollo/client/link/ws'
import { ApolloClient, InMemoryCache } from '@apollo/client'
import { SubscriptionClient } from 'subscriptions-transport-ws'

function isSubscriptionOperation({ query }) {
  const definition = getMainDefinition(query)
  return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
}

// CHANGED: We now use the AuthApiEndpoint obtained by the auth rocket
const httpLink = new HttpLink({
  uri: '<graphqlURL>',
})

// CHANGED: We create an "authLink" that modifies the operation by adding the token to the headers
const authLink = new ApolloLink((operation, forward) => {
  operation.setContext({
    headers: {
      Authorization: 'Bearer <idToken>',
    },
  })
  return forward(operation)
})

// <-- CHANGED: Concatenate the links so that the "httpLink" receives the operation with the headers set by the "authLink"
const httpLinkWithAuth = authLink.concat(httpLink)

const subscriptionClient = new SubscriptionClient('<websocketURL>', {
  reconnect: true,
  // CHANGED: added a "connectionParam" property with a function that returns the `Authorizaiton` header containing our token
  connectionParams: () => {
    return {
      Authorization: 'Bearer <idToken>',
    }
  },
})
const wsLink = new WebSocketLink(subscriptionClient)

const splitLink = split(isSubscriptionOperation, wsLink, httpLinkWithAuth) // Note that we now are using "httpLinkWithAuth"

const client = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(),
})
```

#### Refreshing tokens with Apollo clients

Authorization tokens expire after a certain amount of time. When a token is expired, you will get an error and you will need to call the [refresh the token](https://github.com/boostercloud/rocket-auth-aws-infrastructure#refresh-token) endpoint to get a new token. After you have done so, you need to use the new token in your GraphQL operations.

There are several ways to do this. Here we show the simplest one for learning purposes.

First, we modify the example shown in the section [Sending tokens with apollo clients](#sending-tokens-with-apollo-clients) so that the token is stored in a global variable and the Apollo links get the token from it. That variable will be updated when the user signs-in and the token is refreshed:

```typescript
import { split, HttpLink, ApolloLink } from '@apollo/client'
import { getMainDefinition } from '@apollo/client/utilities'
import { WebSocketLink } from '@apollo/client/link/ws'
import { ApolloClient, InMemoryCache } from '@apollo/client'
import { SubscriptionClient } from 'subscriptions-transport-ws'

let authToken = undefined // <-- CHANGED: This variable will hold the token and will be updated everytime the token is refreshed

function isSubscriptionOperation({ query }) {
  const definition = getMainDefinition(query)
  return definition.kind === 'OperationDefinition' && definition.operation === 'subscription'
}

const httpLink = new HttpLink({
  uri: '<AuthApiEndpoint>',
})

const authLink = new ApolloLink((operation, forward) => {
  if (authToken) {
    operation.setContext({
      headers: {
        Authorization: `Bearer ${authToken}`, // <-- CHANGED: We use the "authToken" global variable
      },
    })
  }
  return forward(operation)
})

const httpLinkWithAuth = authLink.concat(httpLink)

const subscriptionClient = new SubscriptionClient('<websocketURL>', {
  reconnect: true,
  // CHANGED: added a "connectionParam" property with a function that returns the `Authorizaiton` header containing our token
  connectionParams: () => {
    if (authToken) {
      return {
        Authorization: `Bearer ${authToken}`, // <-- CHANGED: We use the "authToken" global variable
      }
    }
    return {}
  },
})
const wsLink = new WebSocketLink(subscriptionClient)

const splitLink = split(isSubscriptionOperation, wsLink, httpLinkWithAuth)

const client = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(),
})
```

Now, _when the user signs-in_ or _when the token is refreshed_, we need to do two things:

1. Update the global variable `authToken` with the new token.
2. Reconnect the socket used by the subscription client by doing `subscriptionClient.close(false)`.

You might be wondering why we need to do the second step. The reason is that, with operations sent through HTTP, the token goes along with every operation, in the headers. However, with operations sent through WebSockets, like subscriptions, the token is only sent when the socket connection is established. For this reason, **everytime we update the token we need to reconnect the `SubscriptionClient`** so that it sends again the token (the updated one in this case).

### The GraphQL over WebSocket protocol

Sockets are channels for two-way communication that doesn't follow the request-response cycle, a characteristic feature of the HTTP protocol. One part can send many messages and the other part can receive all of them but only answer to some specific ones. What is more, messages could come in any order. For example, one part can send two messages and receive the response of the second message before the response of the first message.

For these reasons, in order to have an effective non-trivial communication through sockets, a sub-protocol is needed. It would be in charge of making both parts understand each other, share authentication tokens, matching response to the corresponding requests, etc.

The Booster WebSocket communication uses the "GraphQL over WebSocket" protocol as subprotocol. It is in charge of all the low level stuff needed to properly send subscription operations to read models and receive the corresponding data.

You don't need to know anything about this to develop using Booster, neither in the backend side nor in the frontend side (as all the Apollo GraphQL clients uses this protocol), but it is good to know it is there to guarantee a proper communication. In case you are really curious, you can read about the protocol [here](https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md).

> [!NOTE] The WebSocket communication in Booster only supports this subprotocol, whose identifier is `graphql-ws`. For this reason, when you connect to the WebSocket provisioned by Booster, you must specify the `graphql-ws` subprotocol. If not, the connection won't succeed.

## Cloud native

One of the goals of Booster is to become provider agnostic so you can deploy your application to any serverless provider like AWS, Google Cloud, Azure, etc...

In the current version, we offer full support for AWS provider and experimental support for Kubernetes and Azure providers. We will eventually support all main cloud providers (**Contributions are welcome!** 😜)

### Configure your provider credentials

#### AWS provider

In AWS, it is required that your `~/.aws/credentials` file is properly setup, and a `region` attribute is specified. If you have the [AWS CLI installed](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html), you can create the config file by running the command `aws configure`, but that is completely optional, **AWS CLI is not required to run booster**.

This is an example of the minimal content your `~/.aws/credentials` file should have:

```text
[default]
aws_access_key_id = <YOUR KEY ID>
aws_secret_access_key = <YOUR ACCESS KEY>
region = eu-west-1
```

It's recommended to use IAM user keys and avoiding your root access keys. If you need help obtaining a `KEY ID` and `ACCESS KEY`, [check out the official AWS guides](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey).

### Deploy your project

To deploy your Booster project, run the following command:

```shell
boost deploy -e <environment name>
```

The `<environment name>` parameter is the name of the [environment](chapters/05_going-deeper#environments) you want to deploy.
It will take a while, but you should have your project deployed to your cloud provider.

If you make changes to your code, you can run `boost deploy -e <environment name>` again to update your project in the cloud.

To skip restoring dependencies after deployment you can run `boost deploy -e <environment name> -s`.

### Application outputs

After any deployment, an "Outputs" section will be printed to the console with useful information needed to interact with your application. The meaning of those outputs are:

- **httpURL**: This is the base HTTP URL of your application. You will need it to interact with the
  authentication/authorization API and the GraphQL API.
- **websocketURL**: This is the WebSocket URL you need to use to send GraphQL subscriptions.
- **clientID**: This parameter is _specific for the AWS provider_ (only shown if used AWS when deployint) and is
  needed only for the `auth/sign-up` and `auth/sign-in` endpoints.

### Delete your cloud stack

If you want to delete the Booster application that has been deployed, you can run:

```shell
boost nuke -e <environment name>
```

> [!WARNING] This will delete everything in your stack, including databases. This action is **not** reversible!

For a force delete without asking for confirmation, you can run `boost nuke -e <environment name> -f`.

> [!ATTENTION] Be EXTRA CAUTIOUS with this option, all your application data will be irreversibly DELETED without confirmation.
