"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[176],{8434:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>d,contentTitle:()=>i,default:()=>h,frontMatter:()=>a,metadata:()=>s,toc:()=>l});var r=n(5893),o=n(1151);const a={},i="Read model",s={id:"architecture/read-model",title:"Read model",description:"A read model contains the data of your application that is exposed to the client through the GraphQL API. It's a projection of one or more entities, so you dont have to directly expose them to the client. Booster generates the GraphQL queries that allow you to fetch your read models.",source:"@site/docs/03_architecture/06_read-model.mdx",sourceDirName:"03_architecture",slug:"/architecture/read-model",permalink:"/architecture/read-model",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/06_read-model.mdx",tags:[],version:"current",lastUpdatedBy:"Jorge Rodr\xedguez",lastUpdatedAt:1708603384,formattedLastUpdatedAt:"Feb 22, 2024",sidebarPosition:6,frontMatter:{},sidebar:"docs",previous:{title:"Entity",permalink:"/architecture/entity"},next:{title:"Notifications",permalink:"/architecture/notifications"}},d={},l=[{value:"Creating a read model",id:"creating-a-read-model",level:2},{value:"Declaring a read model",id:"declaring-a-read-model",level:2},{value:"The projection function",id:"the-projection-function",level:2},{value:"Projecting multiple entities",id:"projecting-multiple-entities",level:3},{value:"Advanced join keys",id:"advanced-join-keys",level:3},{value:"Array of entities",id:"array-of-entities",level:4},{value:"Returning special values",id:"returning-special-values",level:3},{value:"Deleting read models",id:"deleting-read-models",level:4},{value:"Keeping read models untouched",id:"keeping-read-models-untouched",level:4},{value:"Nested queries and calculated values using getters",id:"nested-queries-and-calculated-values-using-getters",level:2},{value:"Authorizing a read model",id:"authorizing-a-read-model",level:2},{value:"Querying a read model",id:"querying-a-read-model",level:2},{value:"Filtering a read model",id:"filtering-a-read-model",level:3},{value:"Subscribing to a read model",id:"subscribing-to-a-read-model",level:2},{value:"Sorting Read Models",id:"sorting-read-models",level:2},{value:"Querying time sequences",id:"querying-time-sequences",level:3},{value:"Read models naming convention",id:"read-models-naming-convention",level:2}];function c(e){const t={a:"a",admonition:"admonition",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",h4:"h4",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,o.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"read-model",children:"Read model"}),"\n",(0,r.jsxs)(t.p,{children:["A read model contains the data of your application that is exposed to the client through the GraphQL API. It's a ",(0,r.jsx)(t.em,{children:"projection"})," of one or more entities, so you dont have to directly expose them to the client. Booster generates the GraphQL queries that allow you to fetch your read models."]}),"\n",(0,r.jsxs)(t.p,{children:["In other words, Read Models are cached data optimized for read operations. They're updated reactively when ",(0,r.jsx)(t.a,{href:"entity",children:"Entities"})," are updated after reducing ",(0,r.jsx)(t.a,{href:"event",children:"events"}),"."]}),"\n",(0,r.jsx)(t.h2,{id:"creating-a-read-model",children:"Creating a read model"}),"\n",(0,r.jsx)(t.p,{children:"The Booster CLI will help you to create new read models. You just need to run the following command and the CLI will generate all the boilerplate for you:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-shell",children:'boost new:read-model CartReadModel --fields id:UUID cartItems:"Array<CartItem>" paid:boolean --projects Cart\n'})}),"\n",(0,r.jsxs)(t.p,{children:["This will generate a new file called ",(0,r.jsx)(t.code,{children:"cart-read-model.ts"})," in the ",(0,r.jsx)(t.code,{children:"src/read-models"})," directory. You can also create the file manually, but you will need to create the class and decorate it, so we recommend using the CLI."]}),"\n",(0,r.jsx)(t.h2,{id:"declaring-a-read-model",children:"Declaring a read model"}),"\n",(0,r.jsxs)(t.p,{children:["In Booster, a read model is a class decorated with the ",(0,r.jsx)(t.code,{children:"@ReadModel"})," decorator. The properties of the class are the fields of the read model. The following example shows a read model with two fields:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class ReadModelName {\n  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeType /* as many fields as needed */) {}\n}\n"})}),"\n",(0,r.jsx)(t.admonition,{type:"info",children:(0,r.jsxs)(t.p,{children:["The ",(0,r.jsx)(t.code,{children:"ReadModelName"})," class name will be used as the read model name in the GraphQL schema. Also, the types on the constructor will be used to generate the GraphQL schema. For example, if you have a property of type ",(0,r.jsx)(t.code,{children:"Array<CartItem>"})," the GraphQL schema will know that is an array of ",(0,r.jsx)(t.code,{children:"CartItem"})," objects."]})}),"\n",(0,r.jsx)(t.h2,{id:"the-projection-function",children:"The projection function"}),"\n",(0,r.jsxs)(t.p,{children:["The projection function is a static method decorated with the ",(0,r.jsx)(t.code,{children:"@Projects"})," decorator. It is used to define how the read model is updated when an entity is modified. he projection function must return a new instance of the read model, it receives two arguments:"]}),"\n",(0,r.jsxs)(t.ul,{children:["\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.code,{children:"entity"}),": The entity that has been modified"]}),"\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.code,{children:"current?"}),": The current read model instance. If it's the first time the read model is created, this argument will be ",(0,r.jsx)(t.code,{children:"undefined"})]}),"\n"]}),"\n",(0,r.jsxs)(t.p,{children:["You must provide the ",(0,r.jsx)(t.code,{children:"@Projects"})," decorator with an entity class and the ",(0,r.jsx)(t.strong,{children:(0,r.jsx)(t.em,{children:"join key"})}),". The join key is the name of the field in the entity that is used to match it with the read model's ",(0,r.jsx)(t.code,{children:"id"})," field. In the example below, we are using the ",(0,r.jsx)(t.code,{children:"id"})," field of the ",(0,r.jsx)(t.code,{children:"Cart"})," entity to match it with the ",(0,r.jsx)(t.code,{children:"CartReadModel"})," read model."]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class CartReadModel {\n  public constructor(readonly id: UUID, readonly cartItems: Array<CartItem>, readonly paid: boolean) {}\n\n  // highlight-start\n  @Projects(Cart, 'id')\n  public static projectCart(entity: Cart, currentCartReadModel?: CartReadModel): CartReadModel {\n    return new CartReadModel(entity.id, entity.cartItems, entity.paid)\n  }\n  // highlight-end\n}\n"})}),"\n",(0,r.jsx)(t.h3,{id:"projecting-multiple-entities",children:"Projecting multiple entities"}),"\n",(0,r.jsxs)(t.p,{children:["You are able to project multiple entities into the same read model. For example, you can have a ",(0,r.jsx)(t.code,{children:"UserReadModel"})," that projects both the ",(0,r.jsx)(t.code,{children:"User"})," entity and the ",(0,r.jsx)(t.code,{children:"Post"})," entity. In this case, the join key will be different for each entity:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class UserReadModel {\n  public constructor(readonly username: string /* ...(other interesting fields from users)... */) {}\n\n  // highlight-next-line\n  @Projects(User, 'id')\n  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel> {\n    // Here we update the user fields\n  }\n\n  // highlight-next-line\n  @Projects(Post, 'ownerId')\n  public static projectUserPost(entity: Post, current?: UserReadModel): ProjectionResult<UserReadModel> {\n    //Here we can adapt the read model to show specific user information related with the Post entity\n  }\n}\n"})}),"\n",(0,r.jsx)(t.h3,{id:"advanced-join-keys",children:"Advanced join keys"}),"\n",(0,r.jsx)(t.p,{children:"There might be cases where you need to project an entity into a read model using a more complex join key. For that reason, Booster supports other types of join keys."}),"\n",(0,r.jsx)(t.h4,{id:"array-of-entities",children:"Array of entities"}),"\n",(0,r.jsxs)(t.p,{children:["You can use an array of entities as a join key. For example, if you have a ",(0,r.jsx)(t.code,{children:"Group"})," entity with an array of users in that group (",(0,r.jsx)(t.code,{children:"users: Array<UUID>"}),"), you can have the following to update each ",(0,r.jsx)(t.code,{children:"UserReadModel"})," accordingly:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"  @Projects(Group, 'users')\n  public static projectUserGroup(entity: Group, readModelID: UUID, current?: UserReadModel): ProjectionResult<UserReadModel> {\n    //Here we can update the read models with group information\n    //This logic will be executed for each read model id in the array\n  }\n"})}),"\n",(0,r.jsxs)(t.p,{children:["You can even select arrays of UUIDs as ",(0,r.jsx)(t.code,{children:"joinKey"}),". Booster get each value on the array, find a read model with that id and execute the projection function. The signature of the projection function is a bit different in this case. It receives the ",(0,r.jsx)(t.code,{children:"readModelID"})," as the second argument, which is the id we are projecting from the array. The third argument is the current read model instance, which will be ",(0,r.jsx)(t.code,{children:"undefined"})," if it's the first time the read model is created. For example, if we have a ",(0,r.jsx)(t.code,{children:"Group"})," with an array of users in that group (",(0,r.jsx)(t.code,{children:"users: Array<UUID>"}),"), we can have the following to update each ",(0,r.jsx)(t.code,{children:"UserReadModel"})," accordingly:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"  @Projects(Group, 'users')\n  public static projectUserGroup(entity: Group, readModelID: UUID, current?: UserReadModel): ProjectionResult<UserReadModel> {\n    //Here we can update the read models with group information\n    //This logic will be executed for each read model id in the array\n  }\n"})}),"\n",(0,r.jsx)(t.h3,{id:"returning-special-values",children:"Returning special values"}),"\n",(0,r.jsx)(t.p,{children:"Projections usually return a new instance of the read model. However, there are some special cases where you may want to return a different value."}),"\n",(0,r.jsx)(t.h4,{id:"deleting-read-models",children:"Deleting read models"}),"\n",(0,r.jsxs)(t.p,{children:["One of the most common cases is when you want to delete a read model. For example, if you have a ",(0,r.jsx)(t.code,{children:"UserReadModel"})," that projects the ",(0,r.jsx)(t.code,{children:"User"})," entity, you may want to delete the read model when the user is deleted. In this case you can return the ",(0,r.jsx)(t.code,{children:"ReadModelAction.Delete"})," value:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class UserReadModel {\n  public constructor(readonly username: string, /* ...(other interesting fields from users)... */) {}\n\n  @Projects(User, 'id')\n  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel>  {\n    if (current?.deleted) {\n      return ReadModelAction.Delete\n    }\n    return new UserReadModel(...)\n  }\n"})}),"\n",(0,r.jsx)(t.admonition,{type:"info",children:(0,r.jsx)(t.p,{children:"Deleting a read model is a very expensive operation. It will trigger a write operation in the read model store. If you can, try to avoid deleting read models."})}),"\n",(0,r.jsx)(t.h4,{id:"keeping-read-models-untouched",children:"Keeping read models untouched"}),"\n",(0,r.jsxs)(t.p,{children:["Another common case is when you want to keep the read model untouched. For example, if you have a ",(0,r.jsx)(t.code,{children:"UserReadModel"})," that projects the ",(0,r.jsx)(t.code,{children:"User"})," entity, you may want to keep the read model untouched there are no releveant changes to your read model. In this case you can return the ",(0,r.jsx)(t.code,{children:"ReadModelAction.Nothing"})," value:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class UserReadModel {\n  public constructor(readonly username: string, /* ...(other interesting fields from users)... */) {}\n\n  @Projects(User, 'id')\n  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel>  {\n    if (!current?.modified) {\n      return ReadModelAction.Nothing\n    }\n    return new UserReadModel(...)\n  }\n"})}),"\n",(0,r.jsx)(t.admonition,{type:"info",children:(0,r.jsx)(t.p,{children:"Keeping the read model untouched higly recommended in favour of returning a new instance of the read model with the same data. This will not only prevent a new write operation in the database, making your application more efficient. It will also prevent an unnecessary update to be dispatched to any GrahpQL clients subscribed to that read model."})}),"\n",(0,r.jsx)(t.h2,{id:"nested-queries-and-calculated-values-using-getters",children:"Nested queries and calculated values using getters"}),"\n",(0,r.jsx)(t.p,{children:"You can use TypeScript getters in your read models to allow nested queries and/or return calculated values. You can write arbitrary code in a getter, but you will tipically query for related read model objects or generate a value computed based on the current read model instance or context. This greatly improves the potential of customizing your read model responses."}),"\n",(0,r.jsxs)(t.p,{children:["Here's an example of a getter in the ",(0,r.jsx)(t.code,{children:"UserReadModel"})," class that returns all ",(0,r.jsx)(t.code,{children:"PostReadModel"}),"s that belong to a specific ",(0,r.jsx)(t.code,{children:"UserReadModel"}),":"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@ReadModel\nexport class UserReadModel {\n  public constructor(readonly id: UUID, readonly name: string, private postIds: UUID[]) {}\n\n  public get posts(): Promise<PostReadModel[]> {\n    return this.postIds.map((postId) => Booster.readModel(PostReadModel)\n      .filter({\n        id: { eq: postId }\n      })\n      .search()\n  }\n\n  @Projects(User, 'id')\n  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel>  {\n    return new UserReadModel(entity.id, entity.name, entity.postIds)\n  }\n}\n"})}),"\n",(0,r.jsx)(t.p,{children:"As you can see, the getter posts uses the Booster.readModel(PostReadModel) method and filters it by the ids of the posts saved in the postIds private property. This allows you to retrieve all the PostReadModels that belong to a specific UserReadModel and include them as part of the GraphQL response."}),"\n",(0,r.jsxs)(t.p,{children:["Also, you can see here a simple example of a getter called ",(0,r.jsx)(t.code,{children:"currentTime"})," that returns the timestamp at the moment of the request:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public get currentTime(): Date {\n  return new Date()\n}\n"})}),"\n",(0,r.jsx)(t.p,{children:"With the getters in place, your GraphQL API will start exposing the getters as regular fields and you will be able to transparently read them as follows:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-gql",children:'query {\n  user(id: "123") {\n    id\n    name\n    currentTime\n    posts {\n      id\n      title\n      content\n    }\n  }\n}\n'})}),"\n",(0,r.jsx)(t.p,{children:"And here is an example of the corresponding JSON response when this query is executed:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-json",children:'{\n  "data": {\n    "user": {\n      "id": "123",\n      "name": "John Doe",\n      "currentTime": "2022-09-20T18:30:00.000Z",\n      "posts": [\n        {\n          "id": "1",\n          "title": "My first post",\n          "content": "This is the content of my first post"\n        },\n        {\n          "id": "2",\n          "title": "My second post",\n          "content": "This is the content of my second post"\n        }\n      ]\n    }\n  }\n}\n'})}),"\n",(0,r.jsx)(t.p,{children:"Notice that getters are not cached in the read models database, so the getters will be executed every time you include these fields in the queries. If access to nested queries is frequent or the size of the responses are big, you could improe your API response performance by querying the read models separately and joining the results in the client application."}),"\n",(0,r.jsx)(t.h2,{id:"authorizing-a-read-model",children:"Authorizing a read model"}),"\n",(0,r.jsxs)(t.p,{children:["Read models are part of the public API of a Booster application, so you can define who is authorized to submit them. All read models are protected by default, which means that no one can query them. In order to allow users to query a read model, you must explicitly authorize them. You can use the ",(0,r.jsx)(t.code,{children:"authorize"})," field of the ",(0,r.jsx)(t.code,{children:"@ReadModel"})," decorator to specify the authorization rule."]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/read-model/product-read-model.ts"',children:"@ReadModel({\n  authorize: 'all',\n})\nexport class ProductReadModel {\n  public constructor(public id: UUID, readonly name: string, readonly description: string, readonly price: number) {}\n\n  @Projects(Product, 'id')\n  public static projectProduct(entity: Product, current?: ProductReadModel): ProjectionResult<ProductReadModel> {\n    return new ProductReadModel(entity.id, entity.name, entity.description, entity.price)\n  }\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["You can read more about this on the ",(0,r.jsx)(t.a,{href:"/security/authorization",children:"Authorization section"}),"."]}),"\n",(0,r.jsx)(t.h2,{id:"querying-a-read-model",children:"Querying a read model"}),"\n",(0,r.jsx)(t.p,{children:"Booster read models are accessible to the outside world through GraphQL queries. GrahpQL fits very well with Booster's CQRS approach because it has two kinds of reading operations: Queries and Subscriptions. They are read-only operations that do not modify the state of the application. Booster uses them to fetch data from the read models."}),"\n",(0,r.jsx)(t.p,{children:"Booster automatically creates the queries and subscriptions for each read model. You can use them to fetch the data from the read models. For example, given the following read model:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/read-model/cart-read-model.ts"',children:"@ReadModel({\n  authorize: 'all',\n})\nexport class CartReadModel {\n  public constructor(public id: UUID, readonly items: Array<CartItem>) {}\n\n  @Projects(Cart, 'id')\n  public static projectCart(entity: Cart, currentReadModel: CartReadModel): ProjectionResult<CartReadModel> {\n    return new CartReadModel(entity.id, entity.items)\n  }\n}\n"})}),"\n",(0,r.jsx)(t.p,{children:"You will get the following GraphQL query and subscriptions:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-graphql",children:"query CartReadModel(id: ID!): CartReadModel\nsubscription CartReadModel(id: ID!): CartReadModel\nsubscription CartReadModels(id: UUIDPropertyFilter!): CartReadModel\n"})}),"\n",(0,r.jsxs)(t.p,{children:["For more information about queries and how to use them, please check the ",(0,r.jsx)(t.a,{href:"/graphql",children:"GraphQL API"})," section."]}),"\n",(0,r.jsx)(t.h3,{id:"filtering-a-read-model",children:"Filtering a read model"}),"\n",(0,r.jsxs)(t.p,{children:["Booster GraphQL API provides support for filtering Read Models on ",(0,r.jsx)(t.code,{children:"queries"})," and ",(0,r.jsx)(t.code,{children:"subscriptions"}),". To get more information about it go to the ",(0,r.jsx)(t.a,{href:"/graphql#filtering-a-read-model",children:"GraphQL API"})," section."]}),"\n",(0,r.jsx)(t.h2,{id:"subscribing-to-a-read-model",children:"Subscribing to a read model"}),"\n",(0,r.jsxs)(t.p,{children:["Booster GraphQL API also provides support for real-time updates using subscriptions and a web-socket. To get more information about it go to the ",(0,r.jsx)(t.a,{href:"/graphql#subscribing-to-read-models",children:"GraphQL API"})," section."]}),"\n",(0,r.jsx)(t.h2,{id:"sorting-read-models",children:"Sorting Read Models"}),"\n",(0,r.jsx)(t.p,{children:"There are some cases when it's desirable to query your read models sorted a particular field. An example could be a chat app where you want to fetch the messages of a channel sorted by the time they were sent. Booster provides a special decorator to tag a specific property as a sequence key for a read model:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/read-model/message-read-model.ts"',children:"export class MessageReadModel {\n  public constructor(\n    readonly id: UUID, // A channel ID\n    @sequencedBy readonly timestamp: string,\n    readonly contents: string\n  )\n\n  @Projects(Message, 'id')\n  public static projectMessage(\n    entity: Message,\n    currentReadModel: MessageReadModel\n  ): ProjectionResult<MessageReadModel> {\n    return new MessageReadModel(entity.id, entity.timestamp, entity.contents)\n  }\n}\n"})}),"\n",(0,r.jsx)(t.h3,{id:"querying-time-sequences",children:"Querying time sequences"}),"\n",(0,r.jsx)(t.p,{children:"Adding a sequence key to a read model changes the behavior of the singular query, which now accepts the sequence key as an optional parameter:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-graphql",children:"query MessageReadModel(id: ID!, timestamp: string): [MessageReadModel]\n"})}),"\n",(0,r.jsx)(t.p,{children:"Using this query, when only the id is provided, you get an array of all the messages in the channel sorted by timestamp in ascending order (from older to newer). When you also provide an specific timestamp, you still get an array, but it will only contain the message sent in that exact moment."}),"\n",(0,r.jsxs)(t.p,{children:["It is important to guarantee that the sequence key is unique for each message. This could be difficult to achieve if you are using a timestamp as the sequence key. Booster provides a utility function to generate unique timestamps that you can use in your read models: ",(0,r.jsx)(t.code,{children:"TimeKey.generate()"}),". It will generate a timestamp with a random UUID as a suffix to avoid any coincidences."]}),"\n",(0,r.jsxs)(t.p,{children:["For more information about queries and how to use them, please check the ",(0,r.jsx)(t.a,{href:"/graphql#reading-read-models",children:"GraphQL API"})," section."]}),"\n",(0,r.jsx)(t.h2,{id:"read-models-naming-convention",children:"Read models naming convention"}),"\n",(0,r.jsxs)(t.p,{children:["As it has been previously commented, semantics plays an important role in designing a coherent system and your application should reflect your domain concepts, we recommend choosing a representative domain name and use the ",(0,r.jsx)(t.code,{children:"ReadModel"})," suffix in your read models name."]}),"\n",(0,r.jsxs)(t.p,{children:["Despite you can place your read models in any directory, we strongly recommend you to put them in ",(0,r.jsx)(t.code,{children:"<project-root>/src/read-models"}),". Having all the read models in one place will help you to understand your application's capabilities at a glance."]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-text",children:"<project-root>\n\u251c\u2500\u2500 src\n\u2502\xa0\xa0 \u251c\u2500\u2500 commands\n\u2502\xa0\xa0 \u251c\u2500\u2500 common\n\u2502\xa0\xa0 \u251c\u2500\u2500 config\n\u2502\xa0\xa0 \u251c\u2500\u2500 entities\n\u2502\xa0\xa0 \u251c\u2500\u2500 read-models  <------ put them here\n\u2502\xa0\xa0 \u251c\u2500\u2500 events\n\u2502\xa0\xa0 \u251c\u2500\u2500 index.ts\n\u2502\xa0\xa0 \u2514\u2500\u2500 read-models\n"})})]})}function h(e={}){const{wrapper:t}={...(0,o.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(c,{...e})}):c(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>s,a:()=>i});var r=n(7294);const o={},a=r.createContext(o);function i(e){const t=r.useContext(a);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function s(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:i(e.components),r.createElement(a.Provider,{value:t},e.children)}}}]);