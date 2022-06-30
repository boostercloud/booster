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

In order to start your application using the local provider, use the following command:

```bash
boost start -e local
```

Where `local` is one of your defined environments with the `Booster.configure` call.

## Logging in Booster

If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call to `console.debug`, `console.info`, `console.warn` or `console.error`. In this regard, there's no distinction from any other node process and you'll find the logs in your cloud provider's default log aggregator (i.e. Cloudwatch if you use AWS).

### Advanced logging

If you need advanced logging capabilities such as redirecting your logs to a log aggregator, Booster also supports overriding the default behavior by providing custom loggers. The only thing you need to do is to provide an object that implements the `Logger` interface at config time:

_The Logger interface (In package `@boostercloud/framework-types`):_
```typescript
interface Logger {
  debug(message?: any, ...optionalParams: any[]): void
  info(message?: any, ...optionalParams: any[]): void
  warn(message?: any, ...optionalParams: any[]): void
  error(message?: any, ...optionalParams: any[]): void
}
```

You can set your logger, as well as the log level and your preferred log prefix (Defaults to the string `'Booster'`) in your `config.ts` file for each of your environments:

_In your project's config.ts file:_
```typescript
Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  
  config.logger = new MyCustomLogger() // Overrides the default logger object
  config.logLevel = Level.debug        // Sets the log level at 'debug'     
  config.logPrefix = 'my-store-dev'    // Sets the default prefix
})
```

### Using the Booster's logger

All framework's components will use this logger by default and will generate logs that match the following pattern:

```
[<logPrefix>]|moduleName: <message>
```

You can get a custom logger instance that extends the configured logger by adding your moduleName and optionally overriding the configured prefix with the `getLogger` helper function. It's a good practice to build and use a separate logger instance built with this method for each context, as this will make it easier to filter your logs when you need to investigate a problem.

_Example: Obtaining a logger for your command:_
```typescript
@Command({
  authorize: [User],
})
export class UpdateShippingAddress {
  public constructor(readonly cartId: UUID, readonly address: Address) {}

  public static async handle(command: UpdateShippingAddress, register: Register): Promise<void> {
    const logger = getLogger(Booster.config, 'UpdateShippingCommand#handler', 'MyApp')
    logger.debug(`User ${register.currentUser?.username} changed shipping address for cart ${command.cartId}: ${JSON.stringify(command.address}`)
    register.events(new ShippingAddressUpdated(command.cartId, command.address))
  }
}

```

When a `UpdateShippingAddress` command is handled, it wil log messages that look like the following:
```
[MyApp]|UpdateShippingCommand#handler: User buyer42 changed shipping address for cart 314: { street: '13th rue del percebe', number: 6, ... }
```

Using the configured Booster logger is not mandatory for your application, but it might be convenient to centralize your logs and this is a standard way to do it.

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

In order to use the JWT authorization you will need to configure at least one token verifier. To do this you can add a `TokenVerifierConfig` entry to the `tokenVerifiers` property which contains the following properties:

- `jwksUri`: URI pointing to a [JWKS](https://datatracker.ietf.org/doc/html/rfc7517#section-4) object containing the public keys provided by the auth provider that signed the JWT tokens.
- `publicKey`: Alternatively, you can manually provide a public key using this parameter.
- `issuer`: Identifies the principal that issued the JWT tokens.
- `rolesClaim`: Field where provider contains the token. As an example Cognito uses `cognito:groups`.

This is a config sample for an application that uses Firebase as the auth provider:

```typescript
import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'demoapp'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.tokenVerifiers = [
    {
      jwksUri: 'https://demoapp.firebase.com/.well-known/jwks.json',
      issuer: 'https://securetoken.google.com/demoapp',
      rolesClaim: 'firebase:groups',
    }
  ]
})
```

And this is an extended configuration that accepts both firebase emitted tokens and self-signed tokens that use a certificate stored in a local file:

```typescript
Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'my-app'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.tokenVerifiers = [
    // Accepts tokens signed with a local certificate (For instance, tokens generated for confirmation emails)
    { 
      issuer: 'booster',
      publicKey: fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8'),
      rolesClaim: 'booster:role',
    },
    // Also accepts tokens signed by Firebase
    {
      jwksUri: 'https://myapp.firebase.com/.well-known/jwks.json',
      issuer: 'https://securetoken.google.com/myapp',
      rolesClaim: 'firebase:groups',
    }
  ]
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


### Adding before hooks to your read models

When you send queries or subscriptions to your read models, you can tell Booster to execute some code before executing the operation. These are called `before` hooks, and they receive a `ReadModelRequestEnvelope` object representing the current request.

```typescript
interface ReadModelRequestEnvelope<TReadModel> {
  currentUser?: UserEnvelope // The current authenticated user
  requestID: UUID // An ID assigned to this request
  key?: { // If present, contains the id and sequenceKey that identify a specific read model
    id: UUID
    sequenceKey?: SequenceKey
  }
  className: string // The read model class name
  filters: ReadModelRequestProperties<TReadModel> // Filters set in the GraphQL query
  limit?: number // Query limit if set
  afterCursor?: unknown // For paginated requests, id to start reading from
}
```

In before hooks, you can either abort the request or alter and return the request object to change the behavior of your request. Before hooks are useful for many use cases, but they're especially useful to add fine-grained access control. For example, to enforce a filter that restrict a logged in user to access only read models objects they own.

When a `before` hook throws an exception, the request is aborted and the error is sent back to the user. In order to continue with the request, it's required that the request object is returned.

In order to define a before hook you pass a list of functions with the right signature to the read model decorator `before` parameter:

```typescript
@ReadModel({
  authorize: [User],
  before: [validateUser],
})
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly userId: UUID
  ) {}
  // Your projections go here
}

function validateUser(request: ReadModelRequestEnvelope<CartReadModel>): ReadModelRequestEnvelope<CartReadModel> {
  if (request.filters?.userId?.eq !== request.currentUser?.id) throw NotAuthorizedError("...")
  return request
}
```

You can also define more than one `before` hook for a read model, and they will be chained, sending the resulting request object from a hook to the next one.

> [!NOTE] The order in which filters are specified matters.

```typescript
import { changeFilters } from '../../filters-helper' // You can also use external functions!

@ReadModel({
  authorize: [User],
  before: [validateUser, validateEmail, changeFilters],
})
export class CartReadModel {
  public constructor(
    readonly id: UUID,
    readonly userId: UUID
  ) {}

  // Your projections go here
}

function validateUser(request: ReadModelRequestEnvelope<CartReadModel>): ReadModelRequestEnvelope<CartReadModel> {
  if (request.filters?.userId?.eq !== request.currentUser?.id) throw NotAuthorizedError("...")
  return request
}

function validateEmail(request: ReadModelRequestEnvelope<CartReadModel>): ReadModelRequestEnvelope<CartReadModel> {
  if (!request.filters.email.includes('myCompanyDomain.com')) throw NotAuthorizedError("...")
  return request
}
```

### Adding before hooks to your commands

You can use `before` hooks also in your command handlers, and [they work as the Read Models ones](#Adding-before-hooks-to-your-read-models), with a slight difference: **we don't modify `filters` but `inputs` (the parameters sent with a command)**. Apart from that, it's pretty much the same, here's an example:

```typescript
@Command({
  authorize: [User],
  before: [beforeFn],
})
export class ChangeCartItem {
  public constructor(readonly cartId: UUID, readonly productId: UUID, readonly quantity: number) {
  }
}

function beforeFn(input: CommandInput, currentUser?: UserEnvelope): CommandInput {
  if (input.cartUserId !== currentUser.id) {
    throw NonAuthorizedUserException() // We don't let this user to trigger the command
  }
  return input
}
```

As you can see, we just check if the `cartUserId` is equal to the `currentUser.id`, which is the user id extracted from the auth token. This way, we can throw an exception and avoid this user to call this command.

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
- Currently, only available on the AWS provider.

### Filter & Pagination
#### Filtering a read model

The Booster GraphQL API provides support for filtering Read Models on `queries` and `subscriptions`.

Using the GraphQL API endpoint you can retrieve the schema of your application so you can see what are the filters for every Read Model and its properties. You can filter like this:

Searching for a specific Read Model by `id`

```graphql
query {
  ProductReadModels(filter: { id: { eq: "test-id" } }) {
    id
    sku
    availability
    price
  }
}
```

#### Supported filters

The currently supported filters are the following ones:

##### Boolean filters

| Filter |   Value    |  Description |
| :----- | :--------: | -----------: |
| eq     | true/false |     Equal to |
| ne     | true/false | Not equal to |

Example:

```graphql
query {
  ProductReadModels(filter: { availability: { eq: true } }) {
    id
    sku
    availability
    price
  }
}
```

##### Number filters

| Filter |  Value  |           Description |
| :----- | :-----: | --------------------: |
| eq     |  Float  |              Equal to |
| ne     |  Float  |          Not equal to |
| gt     |  Float  |          Greater than |
| gte    |  Float  | Greater or equal than |
| lt     |  Float  |            Lower than |
| lte    |  Float  |   Lower or equal than |
| in     | [Float] | Exists in given array |

Example:

```graphql
query {
  ProductReadModels(filter: { price: { gt: 200 } }) {
    id
    sku
    availability
    price
  }
}
```

##### String filters

| Filter     |  Value   |                Description |
| :--------- | :------: | -------------------------: |
| eq         |  String  |                   Equal to |
| ne         |  String  |               Not equal to |
| gt         |  String  |               Greater than |
| gte        |  String  |      Greater or equal than |
| lt         |  String  |                 Lower than |
| lte        |  String  |        Lower or equal than |
| in         | [String] |      Exists in given array |
| beginsWith |  String  | Starts with a given substr |
| contains   |  String  |    Contains a given substr |

Example:

```graphql
query {
  ProductReadModels(filter: { sku: { begingsWith: "jewelry" } }) {
    id
    sku
    availability
    price
  }
}
```

Note: `eq` and `ne` are valid filters for checking if a field value is null or not null.

##### Array filters

| Filter   | Value  |             Description |
| :------- | :----: | ----------------------: |
| includes | Object | Includes a given object |

Example:

```graphql
query {
  CartReadModels(filter: { itemsIds: { includes: "test-item" } }) {
    id
    price
    itemsIds
  }
}
```

_Note: Right now, with complex properties in Arrays, you just can filter them if you know the exact value of an element but is not possible to filter from a property of the element. As a workaround, you can use an array of ids of the complex property and filter for that property as in the example above._

##### Filter combinators

All the filters can be combined to create a more complex search on the same properties of the ReadModel.

| Filter |     Value     |                                      Description |
| :----- | :-----------: | -----------------------------------------------: |
| and    |   [Filters]   |       AND - all filters on the list have a match |
| or     |   [Filters]   | OR - At least one filter of the list has a match |
| not    | Filter/and/or |            The element does not match the filter |

Example:

```graphql
query {
  CartReadModels(filter: { or: [{ id: { contains: "a" } }, { id: { contains: "b" } }] }) {
    id
    price
    itemsIds
  }
}
```

##### IsDefined operator


| Filter    |    Value    |         Description |
|:----------|:-----------:|--------------------:|
| isDefined | true/false  | field exists or not |

Example:

```graphql
query {
  CartReadModels(filter: { price: { isDefined: true } }) {
    id
    price
    itemsIds
  }
}
```

#### Getting and filtering read models data at code level

Booster allows you to get your read models data in your commands handlers and event handlers using the `Booster.readModel` method.

For example, you can filter and get the total number of the products that meet your criteria in your commands like this:

```typescript
@Command({
  authorize: 'all',
})
export class GetProductsCount {
  public constructor(readonly filters: Record<string, any>) {}

  public static async handle(): Promise<void> {
    const searcher = Booster.readModel(ProductReadModel)

    searcher.filter({
      sku: { contains: 'toy' },
      or: [
        {
          description: { contains: 'fancy' },
        },
        {
          description: { contains: 'great' },
        },
      ],
    })

    const result = await searcher.search()
    return { count: result.length }
  }
}
```

> **Warning**: Notice that `ReadModel`s are eventually consistent objects that are calculated as all events in all entities that affect the read model are settled. You should not assume that a read model is a proper source of truth, so you shouldn't use this feature for data validations. If you need to query the most up-to-date current state, consider fetching your Entities, instead of ReadModels, with `Booster.entity`

#### Using sorting

Booster allows you to sort your read models data in your commands handlers and event handlers using the `Booster.readModel` method.

For example, you can sort and get the products in your commands like this:

```graphql
{
  ListCartReadModels(filter: {}, limit: 5, sortBy: {
    shippingAddress: {
      firstName: ASC
    }
  }) {
    items {
      id
      cartItems 
      checks
      shippingAddress {
        firstName
      }
      payment {
        cartId
      }
      cartItemsIds
    }
    cursor
  }
}
```

This is a preview feature available only for some Providers and with some limitations:
* Azure: 
  * Sort by one field supported. 
  * Nested fields supported. 
  * Sort by more than one file: **unsupported**.
* Local:
  * Sort by one field supported.
  * Nested fields supported.
  * Sort by more than one file: **unsupported**.

> **Warning**: It is not possible to sort by fields defined as Interface, only classes or primitives types.


#### Using pagination

The Booster GraphQL API includes a type for your read models that stands for `List{"your-read-model-name"}`, which is the official way to work with pagination. Alternative, there is another type without the `List` prefix, which will be deprecated in future versions.

The Read Model List type includes some new parameters that can be used on queries:
 * `limit`; an integer that specifies the maximum number of items to be returned.
 * `afterCursor`; a parameter to set the `cursor` property returned by the previous query, if not null.

 Example:
```graphql
query {
  ListProductReadModels
  (
    limit: 1,
    afterCursor: { id: "last-page-item"}
  ) {
    id
    sku
    availability
    price
  }
}
```

Besides the parameters, this type also returns a type `{your-read-model-name}Connection`, it includes the following properties:
 * `cursor`; if there are more results to paginate, it will return the object to pass to the `afterCursor` parameter on the next query. If there aren't more items to be shown, it will be undefined.
 * `items`; the list of items returned by the query, if there aren't any, it will be an empty list.

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
 

## Error handling

Booster includes a global error handler annotation `@GlobalErrorHandler` that will catch all errors that are thrown by:
* **Command Handling Errors**: Errors thrown by the `handle` method of the command.
  **Program handling errors**: Errors thrown by the ScheduledCommand `handle` method.
  **Event Handle errors**: Errors thrown by the `Event Handle` method.
* **Reducer errors**: Errors thrown by the `@Reduces` method of the entity.
* **Projection errors**: Errors thrown in the ReadModel `@Projects` method.
* All errors: Errors thrown in any of the previous methods. This method will always be called, also when calling any of the above methods.

You can trap and return new errors in any of these methods annotating a class with `@GlobalErrorHandler` and implementing the following methods:

**Command handle errors**:
```typescript
onCommandHandlerError?(error: Error, command: CommandEnvelope): Promise<Error | undefined>
```

**Schedule handle errors**:
```typescript
onScheduledCommandHandlerError?(error: Error): Promise<Error | undefined>
```

**Event handler errors**:
```typescript
onDispatchEventHandlerError?(error: Error, eventInstance: EventInterface): Promise<Error | undefined>
```

**Reducer errors**:
```typescript
onReducerError?(error: Error, eventInstance: EventInterface, snapshotInstance: EntityInterface | null): Promise<Error | undefined>
```

**Projections errors**:
```typescript
onProjectionError?(error: Error, entity: EntityInterface, readModel: ReadModelInterface | undefined): Promise<Error | undefined>
```

**All errors**
```typescript
  onError?(error: Error | undefined): Promise<Error | undefined>
```

Example:
```typescript
@GlobalErrorHandler()
export class AppErrorHandler {
  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {
    return error
  }

  public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
    return error
  }

  public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error | undefined> {
    return error
  }

  public static async onReducerError(
    error: Error,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error | undefined> {
    return error
  }

  public static async onProjectionError(
    error: Error,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    return error
  }

  public static async onError(error: Error | undefined): Promise<Error | undefined> {
    return error
  }
}
```

**Note**: if you want to ignore the error thrown, you can simply return `undefined` from the error handler.

## Migrations


### Schema migrations

Booster handle classes annotated with `@Migrates` as **Schema migrations**. The migration process will update an existing object 
from one version to the next one. 

For example, to migrate a `Product` entity from version 1 to version 2 we need the following migration class: 

```typescript
@Migrates(Product)
export class ProductMigration {
  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {
    return new ProductV2(
      old.id,
      old.sku,
      old.name,
      old.description,
      old.price,
      old.pictures,
      old.deleted
    )
  }
}
```

The `ProductV1` class is the old version of the `Product` object. You can keep your old clases in the same migration file, for example:

```typescript
class ProductV1 {
  public constructor(
    public id: UUID,
    readonly sku: string,
    readonly name: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    public deleted: boolean = false
  ) {}
}

class ProductV2 extends Product {}
```

### Data migrations

The annotation `@DataMigration` in a class will indicate **Booster** that this class contains data migration code.

```typescript
@DataMigration({
  order: 2,
})
```

When the method `BoosterDataMigrations.run()` is call by the user, a new `BoosterDataMigrationStarted` event is emitted and **Booster** 
will check if there are pending migrations and, if so, run them in the order specified by the `order` value.

User should emit `BoosterDataMigrationFinished` manually at the end of each `DataMigration.start` method.

In `@DataMigration` classes, you can use `Booster.migrateEntity` method. This method will generate an internal event `BoosterEntityMigrated` before migrating the entity data.

This method will receive the old entity name, the old entity id and the new entity that we will be persisted. This way, you can migrate an entity id or rename it.

Example:

```typescript
@DataMigration({
  order: 2,
})
export class CartIdDataMigrateV2 {
  public constructor() {}


  public static async start(register: Register): Promise<void> {
    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)
    const paginatedEntityIdResults = entitiesIdsResult.items

    const carts = await Promise.all(
      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))
    )
    return await Promise.all(
        carts.map(async (cart) => {
          cart.cartItems[0].quantity = 100
          const newCart = new Cart(cart.id, cart.cartItems, cart.shippingAddress, cart.checks)
          await Booster.migrateEntity('Cart', validCart.id, newCart)
          return validCart.id
      })
    )

    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))
  }
}
```


## Provider feature matrix

|                   **Feature**                   |                                                                                                                                                                                          **Description**                                                                                                                                                                                          |                                                                                                                                                           **AWS Serverless** Implemented with the AWS CDK                                                                                                                                                           |                                                                                                                                             **Azure Serverless** Implemented with Azure API                                                                                                                                            |                                                                                                                                                     **Kubernetes** Implemented using Microsoft DAPR                                                                                                                                                    |                                                                                                                            **Local Development** Environment                                                                                                                           |  **Google Cloud** |
|:-------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------:|
| **Core features (Open Source)**                   |                                                                                                                                                                                                                                                                                                                                                                                               |                                                                                                                                                                                                                                                                                                                                                                 |                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                    |               |
| **Commands**                                    | Define the write API. Generates a GraphQL mutation from a command class. When called, the handler method is executed, which can contain arbitrary code (very often, commands end up publishing events)                                                                                                                                                                                        | API Gateway - HTTP request routing. AWS Lambda - Runs the code on demand.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                                                                              | Azure API Management - HTTP request routing. Azure Functions - Runs the code on demand  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                                    | Ingress - HTTP request routing Containerized Node runtime - Runs an express server that processes the requests  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                            | The API is exposed with an express application that runs the command handlers.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                            | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Event Store**                                 | Stores all Booster events and guarantees that all events targeting a specific entity are processed in order at scale. Provides an event-level API that can be consumed by external services.                                                                                                                                                                                                  | DynamoDB - Storage of events   ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                                                                                                                          | CosmosDB - Storage of events  ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) The public events API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                        | Redis - Storage of events (Currently migrating to MongoDB)  ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) The public events API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                          | NeDB - Storage of events in the local hard drive (Manages message ordering. No internal indexes are used)  ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) The public events API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                           | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Event Handlers**                              | Functions triggered by specific events that can run arbitrary code (often publishing other events).                                                                                                                                                                                                                                                                                           | DynamoDB Streams - Triggers lambda for changes in the event store table. AWS Lambda - Run the handlers triggered by the events.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                        | Change feed - Triggers azure functions for changes in the event store. Azure Functions - Run the handler functions  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                        | Event handlers are triggered after an event is stored successfully. Containerized Node runtime - Runs the handler functions.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                              | Event handlers are triggered from the command handler process.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                            | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Entities**                                    | Define the data structures that  represent the state of the system. Snapshooting is handled automatically via reduction functions.                                                                                                                                                                                                                                                            | DynamoDB Streams - Triggers lambda for changes in the event store table. AWS Lambda - Run the event reduction functions to produce the next version of the entity. DynamoDB - Entity Snapshots storage.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                | Change feed - Triggers azure functions for changes in the event store. Azure Functions - Run the event reduction functions to produce the next version of the entity. CosmosDB - Entity Snapshots storage.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                | Entity snapshots are triggered when the Command process finishes. Containerized Node runtime - Run the event reduction functions to produce the next version of the entity. Redis - Entity Snapshots storage (Currently migrating to MongoDB).  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                            | Entity snapshots are triggered from the command handler process. NeDB - Entity Snapshots storage.   ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                        | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Read Models**                                 | Define the read API. Generates GraphQL queries and subscriptions to query the system from the outside. Read Models are projections of the entities, and can create aggregation objects that combine two or more entities.                                                                                                                                                                     | DynamoDB - Read Model objects storage. API Gateway - HTTP request routing for queries, and websocket subscriptions handling.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                           | CosmosDB - Read Model objects storage. Azure API Management - HTTP request routing.  ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) It doesn’t support websockets (GraphQL Subscriptions), but the database can be polled using Queries. Complex queries including several conditions and pagination are ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) Time sequences API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) | Redis - Read Model objects storage (Currently migrating to MongoDB). Ingress - HTTP request routing  ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) It doesn’t support websockets (GraphQL Subscriptions), but the database can be polled using Queries. Complex queries including several conditions and pagination are ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) Time sequences API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) | NeDB - Read Model objects storage.   ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue) It doesn’t support websockets (GraphQL Subscriptions), but the database can be polled using Queries. Complex queries including several conditions and pagination are ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) Time sequences API is ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Scheduled Commands**                          | Commands that are triggered by a scheduler instead of an API call.                                                                                                                                                                                                                                                                                                                            | Cloudwatch Events - Trigger lambda execution on a predefined schedule. AWS Lambda - Runs the command handler code.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                                     |  ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                              |  ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                              |  ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                              | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **JWT authorization**                           | Commands and Read Models support role-based authorization and filters to avoid unauthorized use of the applications developed with Booster. This feature works with most auth providers available (Auth0, Firebase, Cognito or custom implementations that generate JWT tokens).                                                                                                              | The functionality is embedded in the core, so is available for all providers.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                                                                          |                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                    | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)    |
| **Features provided by rockets**                |                                                                                                                                                                                                                                                                                                                                                                                               |                                                                                                                                                                                                                                                                                                                                                                 |                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                                                                                    |                                                                                                                                                                                                                                                                                    |               |
| **JWT Authentication provider**  (Open Source)    | Adds extra HTTP endpoints to implement registration and authorization of users, generating valid JWT tokens that can be consumed by the core.                                                                                                                                                                                                                                                 | Amazon Cognito - Manages registration and the users database.  ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)                                                                                                                                                                                                                                                                          | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **Automated Backups** (Open Source)               | Uses backup services to back up the event store and read model tables in cheaper storage.                                                                                                                                                                                                                                                                                                     | DynamoDB data export to Amazon S3  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **CSV Batch File Processing**  (Request access)          | Processes CSV files dropped in a specific bucket, transforming each row into a Booster event that is injected into the event store and can be processed with regular event handlers or aggregated with entities.                                                                                                                                                                              | Amazon S3 - For the drop zone. AWS Lambda - Triggered by file upload events.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                                                                           | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **File uploads**  (Request access)                       | Enables a file uploading API and publishes a Booster event when new files are uploaded.                                                                                                                                                                                                                                                                                                       | API Gateway - Route HTTP calls to S3 Amazon S3 - Upload API and file storage AWS Lambda - Triggered by file upload events.  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                             | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **Static Site deployment** (Open Source)          | Deploys and publishes a local folder as a static website. Useful to deploy a frontend web application along with the backend.                                                                                                                                                                                                                                                                 | Amazon S3 - To store the static files Amazon Cloudfront - for CDN Distribution  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                                                                                                         | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **Audio-to-text Transcriptions**  (Request access)       | Sends audio files to transcription services to transcribe the conversations in it to text.                                                                                                                                                                                                                                                                                                    | Amazon S3 - Stores the audio and text files. AWS Lambda - Manages the transcription process and notifies its status as events. AWS Transcribe - Audio processing  ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)                                                                                                                                                                       | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **Apache Kafka Seamless Integration**  (Request access)  | Connects a Booster application to a Kafka broker either as a subscriber or a producer for a specific topic: Consumer: Messages consumed are automatically transformed into Booster events Producer: Events configured to be sent to a Kafka topic are automatically sent to Kafka. This rocket is an integration rocket; it does not deploy a Kafka cluster, but connects to an existing one. | Amazon Lambda (using Kafka SelfManagedEventSource) - Triggered automatically when there are new messages in the Kafka topic. Amazon Lambda (using DynamoDBEventSourcing) - Triggered when new events are added to the events store.  ![Proof of concept](https://img.shields.io/badge/Status-Proof%20Of%20Concept-blueviolet) Support for plain-text JSON messages Does not support Schema registry and AVRO message format yet. | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **Stripe API integration PoC** (Request access)          | Integration with Stripe events via webhook, transforming them into Booster events.                                                                                                                                                                                                                                                                                                            | API Gateway - Route the Stripe callbacks to lambda. AWS Lambda - Handles the callback and produces Booster events  ![Proof of concept](https://img.shields.io/badge/Status-Proof%20Of%20Concept-blueviolet)                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |
| **AWS-to-Azure data migration**  (Request access)        | ![Proof of concept](https://img.shields.io/badge/Status-Proof%20Of%20Concept-blueviolet) rocket to seamlessly migrate a Booster application’s data from AWS to Azure when switching providers.                                                                                                                                                                                                                                                                        | AWS Step Functions - Handle the connection to Azure CosmosDB and the data migration.                                                                                                                                                                                                                                                                            | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)                                                                                                                                                                                                                                                                      | ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive) |

### Status guide

| ![Production Ready](https://img.shields.io/badge/Status-Production%20Ready-green)  | Before merging a Pull Request and before releasing a new version, we run a complete test suite that covers three categories:  Full coverage of unit tests Integration tests with actual deployment Load tests The code is currently being used in production in internal products There are external companies running this version on their own to build their own projects |   |   |   |   |   |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-:|:-:|:-:|:-:|:-:|
| ![Feature complete](https://img.shields.io/badge/Status-Feature%20Complete-yellow)  | The code has partial unit test, integration and load tests coverage. The code has been tested manually and used in non-critical use cases on bigger applications or PoC projects.                                                                                                                                                                                            |   |   |   |   |   |
| ![Partial Support](https://img.shields.io/badge/Status-Partial%20Support-blue)   | Implements part of the features, but the parts that are implemented are partially tested and used in non-critical use cases and PoC projects.                                                                                                                                                                                                                                |   |   |   |   |   |
| ![Proof of concept](https://img.shields.io/badge/Status-Proof%20Of%20Concept-blueviolet)  | The code is not currently used in production applications and might have some limitations or need bug fixes.                                                                                                                                                                                                                                                                 |   |   |   |   |   |
| ![To Be Done](https://img.shields.io/badge/Status-To%20Be%20Done-inactive)     | Development has not started yet.                                                                                                                                                                                                                                                                                                                                             |   |   |   |   |   |
