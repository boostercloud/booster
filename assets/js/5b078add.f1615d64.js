"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[1422],{4074:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>d,contentTitle:()=>s,default:()=>h,frontMatter:()=>i,metadata:()=>c,toc:()=>l});var r=t(5893),a=t(1151),o=t(5163);const i={},s="Command",c={id:"architecture/command",title:"Command",description:"Commands are any action a user performs on your application. For example, RemoveItemFromCart, RatePhoto or AddCommentToPost. They express the intention of an user, and they are the main interaction mechanism of your application. They are a similar to the concept of a request on a REST API. Command issuers can also send data on a command as parameters.",source:"@site/docs/03_architecture/02_command.mdx",sourceDirName:"03_architecture",slug:"/architecture/command",permalink:"/architecture/command",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/02_command.mdx",tags:[],version:"current",lastUpdatedBy:"Nick Seagull",lastUpdatedAt:1706202233,formattedLastUpdatedAt:"Jan 25, 2024",sidebarPosition:2,frontMatter:{},sidebar:"docs",previous:{title:"Booster architecture",permalink:"/architecture/event-driven"},next:{title:"Event",permalink:"/architecture/event"}},d={},l=[{value:"Creating a command",id:"creating-a-command",level:2},{value:"Declaring a command",id:"declaring-a-command",level:2},{value:"The command handler function",id:"the-command-handler-function",level:2},{value:"Registering events",id:"registering-events",level:3},{value:"Returning a value",id:"returning-a-value",level:3},{value:"Validating data",id:"validating-data",level:3},{value:"Throw an error",id:"throw-an-error",level:4},{value:"Register error events",id:"register-error-events",level:4},{value:"Reading entities",id:"reading-entities",level:3},{value:"Authorizing a command",id:"authorizing-a-command",level:2},{value:"Submitting a command",id:"submitting-a-command",level:2},{value:"Commands naming convention",id:"commands-naming-convention",level:2}];function m(e){const n={a:"a",admonition:"admonition",code:"code",h1:"h1",h2:"h2",h3:"h3",h4:"h4",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,a.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(n.h1,{id:"command",children:"Command"}),"\n",(0,r.jsxs)(n.p,{children:["Commands are any action a user performs on your application. For example, ",(0,r.jsx)(n.code,{children:"RemoveItemFromCart"}),", ",(0,r.jsx)(n.code,{children:"RatePhoto"})," or ",(0,r.jsx)(n.code,{children:"AddCommentToPost"}),". They express the intention of an user, and they are the main interaction mechanism of your application. They are a similar to the concept of a ",(0,r.jsx)(n.strong,{children:"request on a REST API"}),". Command issuers can also send data on a command as parameters."]}),"\n",(0,r.jsx)(n.h2,{id:"creating-a-command",children:"Creating a command"}),"\n",(0,r.jsx)(n.p,{children:"The Booster CLI will help you to create new commands. You just need to run the following command and the CLI will generate all the boilerplate for you:"}),"\n",(0,r.jsx)(o.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"boost new:command CreateProduct --fields sku:SKU displayName:string description:string price:Money\n"})})}),"\n",(0,r.jsxs)(n.p,{children:["This will generate a new file called ",(0,r.jsx)(n.code,{children:"create-product"})," in the ",(0,r.jsx)(n.code,{children:"src/commands"})," directory. You can also create the file manually, but you will need to create the class and decorate it, so we recommend using the CLI."]}),"\n",(0,r.jsx)(n.h2,{id:"declaring-a-command",children:"Declaring a command"}),"\n",(0,r.jsxs)(n.p,{children:["In Booster you define them as TypeScript classes decorated with the ",(0,r.jsx)(n.code,{children:"@Command"})," decorator. The ",(0,r.jsx)(n.code,{children:"Command"})," parameters will be declared as properties of the class."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/command-name.ts"',children:"@Command()\nexport class CommandName {\n  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeOtherType) {}\n}\n"})}),"\n",(0,r.jsxs)(n.p,{children:["These commands are handled by ",(0,r.jsx)(n.code,{children:"Command Handlers"}),", the same way a ",(0,r.jsx)(n.strong,{children:"REST Controller"})," do with a request. To create a ",(0,r.jsx)(n.code,{children:"Command handler"})," of a specific Command, you must declare a ",(0,r.jsx)(n.code,{children:"handle"})," class function inside the corresponding command you want to handle. For example:"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/command-name.ts"',children:"@Command()\nexport class CommandName {\n  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeOtherType) {}\n\n  // highlight-start\n  public static async handle(command: CommandName, register: Register): Promise<void> {\n    // Validate inputs\n    // Run domain logic\n    // register.events([event1,...])\n  }\n  // highlight-end\n}\n"})}),"\n",(0,r.jsxs)(n.p,{children:["Booster will then generate the GraphQL mutation for the corresponding command, and the infrastructure to handle them. You only have to define the class and the handler function. Commands are part of the public API, so you can define authorization policies for them, you can read more about this on ",(0,r.jsx)(n.a,{href:"/security/authorization",children:"the authorization section"}),"."]}),"\n",(0,r.jsx)(n.admonition,{type:"tip",children:(0,r.jsx)(n.p,{children:"We recommend using command handlers to validate input data before registering events into the event store because they are immutable once there."})}),"\n",(0,r.jsx)(n.h2,{id:"the-command-handler-function",children:"The command handler function"}),"\n",(0,r.jsxs)(n.p,{children:["Each command class must have a method called ",(0,r.jsx)(n.code,{children:"handle"}),". This function is the command handler, and it will be called by the framework every time one instance of this command is submitted. Inside the handler you can run validations, return errors, query entities to make decisions, and register relevant domain events."]}),"\n",(0,r.jsx)(n.h3,{id:"registering-events",children:"Registering events"}),"\n",(0,r.jsxs)(n.p,{children:["Within the command handler execution, it is possible to register domain events. The command handler function receives the ",(0,r.jsx)(n.code,{children:"register"})," argument, so within the handler, it is possible to call ",(0,r.jsx)(n.code,{children:"register.events(...)"})," with a list of events."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/create-product.ts"',children:"@Command()\nexport class CreateProduct {\n  public constructor(readonly sku: string, readonly price: number) {}\n\n  public static async handle(command: CreateProduct, register: Register): Promise<string> {\n    // highlight-next-line\n    register.event(new ProductCreated(/*...*/))\n  }\n}\n"})}),"\n",(0,r.jsxs)(n.p,{children:["For more details about events and the register parameter, see the ",(0,r.jsx)(n.a,{href:"/architecture/event",children:(0,r.jsx)(n.code,{children:"Events"})})," section."]}),"\n",(0,r.jsx)(n.h3,{id:"returning-a-value",children:"Returning a value"}),"\n",(0,r.jsxs)(n.p,{children:["The command handler function can return a value. This value will be the response of the GraphQL mutation. By default, the command handler function expects you to return a ",(0,r.jsx)(n.code,{children:"void"})," as a return type. Since GrahpQL does not have a ",(0,r.jsx)(n.code,{children:"void"})," type, the command handler function returns ",(0,r.jsx)(n.code,{children:"true"})," when called through the GraphQL. This is because the GraphQL specification requires a response, and ",(0,r.jsx)(n.code,{children:"true"})," is the most appropriate value to represent a successful execution with no return value."]}),"\n",(0,r.jsxs)(n.p,{children:["If you want to return a value, you can change the return type of the handler function. For example, if you want to return a ",(0,r.jsx)(n.code,{children:"string"}),":"]}),"\n",(0,r.jsx)(n.p,{children:"For example:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/create-product.ts"',children:"@Command()\nexport class CreateProduct {\n  public constructor(readonly sku: string, readonly price: number) {}\n\n  public static async handle(command: CreateProduct, register: Register): Promise<string> {\n    register.event(new ProductCreated(/*...*/))\n    // highlight-next-line\n    return 'Product created!'\n  }\n}\n"})}),"\n",(0,r.jsx)(n.h3,{id:"validating-data",children:"Validating data"}),"\n",(0,r.jsx)(n.admonition,{type:"tip",children:(0,r.jsxs)(n.p,{children:["Booster uses the typed nature of GraphQL to ensure that types are correct before reaching the handler, so ",(0,r.jsx)(n.strong,{children:"you don't have to validate types"}),"."]})}),"\n",(0,r.jsx)(n.h4,{id:"throw-an-error",children:"Throw an error"}),"\n",(0,r.jsx)(n.p,{children:"A command will fail if there is an uncaught error during its handling. When a command fails, Booster will return a detailed error response with the message of the thrown error. This is useful for debugging, but it is also a security feature. Booster will never return an error stack trace to the client, so you don't have to worry about exposing internal implementation details."}),"\n",(0,r.jsx)(n.p,{children:"One case where you might want to throw an error is when the command is invalid because it breaks a business rule. For example, if the command contains a negative price. In that case, you can throw an error in the handler. Booster will use the error's message as the response to make it descriptive. For example, given this command:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/create-product.ts"',children:"@Command()\nexport class CreateProduct {\n  public constructor(readonly sku: string, readonly price: number) {}\n\n  public static async handle(command: CreateProduct, register: Register): Promise<void> {\n    const priceLimit = 10\n    if (command.price >= priceLimit) {\n      // highlight-next-line\n      throw new Error(`price must be below ${priceLimit}, and it was ${command.price}`)\n    }\n  }\n}\n"})}),"\n",(0,r.jsx)(n.p,{children:"You'll get something like this response:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-json",children:'{\n  "errors": [\n    {\n      "message": "price must be below 10, and it was 19.99",\n      "path": ["CreateProduct"]\n    }\n  ]\n}\n'})}),"\n",(0,r.jsx)(n.h4,{id:"register-error-events",children:"Register error events"}),"\n",(0,r.jsx)(n.p,{children:"There could be situations in which you want to register an event representing an error. For example, when moving items with insufficient stock from one location to another:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/move-stock.ts"',children:"@Command()\nexport class MoveStock {\n  public constructor(\n    readonly productID: string,\n    readonly origin: string,\n    readonly destination: string,\n    readonly quantity: number\n  ) {}\n\n  public static async handle(command: MoveStock, register: Register): Promise<void> {\n    if (!command.enoughStock(command.productID, command.origin, command.quantity)) {\n      // highlight-next-line\n      register.events(new ErrorEvent(`There is not enough stock for ${command.productID} at ${command.origin}`))\n    } else {\n      register.events(new StockMoved(/*...*/))\n    }\n  }\n\n  private enoughStock(productID: string, origin: string, quantity: number): boolean {\n    /* ... */\n  }\n}\n"})}),"\n",(0,r.jsx)(n.p,{children:"In this case, the command operation can still be completed. An event handler will take care of that `ErrorEvent and proceed accordingly."}),"\n",(0,r.jsx)(n.h3,{id:"reading-entities",children:"Reading entities"}),"\n",(0,r.jsxs)(n.p,{children:["Event handlers are a good place to make decisions and, to make better decisions, you need information. The ",(0,r.jsx)(n.code,{children:"Booster.entity"})," function allows you to inspect the application state. This function receives two arguments, the ",(0,r.jsx)(n.code,{children:"Entity"}),"'s name to fetch and the ",(0,r.jsx)(n.code,{children:"entityID"}),". Here is an example of fetching an entity called ",(0,r.jsx)(n.code,{children:"Stock"}),":"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/move-stock.ts"',children:"@Command()\nexport class MoveStock {\n  public constructor(\n    readonly productID: string,\n    readonly origin: string,\n    readonly destination: string,\n    readonly quantity: number\n  ) {}\n\n  public static async handle(command: MoveStock, register: Register): Promise<void> {\n    // highlight-next-line\n    const stock = await Booster.entity(Stock, command.productID)\n    if (!command.enoughStock(command.origin, command.quantity, stock)) {\n      register.events(new ErrorEvent(`There is not enough stock for ${command.productID} at ${command.origin}`))\n    }\n  }\n\n  private enoughStock(origin: string, quantity: number, stock?: Stock): boolean {\n    const count = stock?.countByLocation[origin]\n    return !!count && count >= quantity\n  }\n}\n"})}),"\n",(0,r.jsx)(n.h2,{id:"authorizing-a-command",children:"Authorizing a command"}),"\n",(0,r.jsxs)(n.p,{children:["Commands are part of the public API of a Booster application, so you can define who is authorized to submit them. All commands are protected by default, which means that no one can submit them. In order to allow users to submit a command, you must explicitly authorize them. You can use the ",(0,r.jsx)(n.code,{children:"authorize"})," field of the ",(0,r.jsx)(n.code,{children:"@Command"})," decorator to specify the authorization rule."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",metastring:'title="src/commands/create-product.ts"',children:"@Command({\n  // highlight-next-line\n  authorize: 'all',\n})\nexport class CreateProduct {\n  public constructor(\n    readonly sku: Sku,\n    readonly displayName: string,\n    readonly description: string,\n    readonly price: number\n  ) {}\n\n  public static async handle(command: CreateProduct, register: Register): Promise<void> {\n    register.events(/* YOUR EVENT HERE */)\n  }\n}\n"})}),"\n",(0,r.jsxs)(n.p,{children:["You can read more about this on the ",(0,r.jsx)(n.a,{href:"/security/authorization",children:"Authorization section"}),"."]}),"\n",(0,r.jsx)(n.h2,{id:"submitting-a-command",children:"Submitting a command"}),"\n",(0,r.jsx)(n.p,{children:"Booster commands are accessible to the outside world as GraphQL mutations. GrahpQL fits very well with Booster's CQRS approach because it has two kinds of operations: Mutations and Queries. Mutations are actions that modify the server-side data, just like commands."}),"\n",(0,r.jsxs)(n.p,{children:["Booster automatically creates one mutation per command. The framework infers the mutation input type from the command fields. Given this ",(0,r.jsx)(n.code,{children:"CreateProduct"})," command:"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"@Command({\n  authorize: 'all',\n})\nexport class CreateProduct {\n  public constructor(\n    readonly sku: Sku,\n    readonly displayName: string,\n    readonly description: string,\n    readonly price: number\n  ) {}\n\n  public static async handle(command: CreateProduct, register: Register): Promise<void> {\n    register.events(/* YOUR EVENT HERE */)\n  }\n}\n"})}),"\n",(0,r.jsx)(n.p,{children:"Booster generates the following GraphQL mutation:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-graphql",children:"mutation CreateProduct($input: CreateProductInput!): Boolean\n"})}),"\n",(0,r.jsxs)(n.p,{children:["where the schema for ",(0,r.jsx)(n.code,{children:"CreateProductInput"})," is"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-text",children:"{\n  sku: String\n  displayName: String\n  description: String\n  price: Float\n}\n"})}),"\n",(0,r.jsx)(n.h2,{id:"commands-naming-convention",children:"Commands naming convention"}),"\n",(0,r.jsxs)(n.p,{children:["Semantics are very important in Booster as it will play an essential role in designing a coherent system. Your application should reflect your domain concepts, and commands are not an exception. Although you can name commands in any way you want, we strongly recommend you to ",(0,r.jsx)(n.strong,{children:"name them starting with verbs in imperative plus the object being affected"}),". If we were designing an e-commerce application, some commands would be:"]}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsx)(n.li,{children:"CreateProduct"}),"\n",(0,r.jsx)(n.li,{children:"DeleteProduct"}),"\n",(0,r.jsx)(n.li,{children:"UpdateProduct"}),"\n",(0,r.jsx)(n.li,{children:"ChangeCartItems"}),"\n",(0,r.jsx)(n.li,{children:"ConfirmPayment"}),"\n",(0,r.jsx)(n.li,{children:"MoveStock"}),"\n",(0,r.jsx)(n.li,{children:"UpdateCartShippingAddress"}),"\n"]}),"\n",(0,r.jsxs)(n.p,{children:["Despite you can place commands, and other Booster files, in any directory, we strongly recommend you to put them in ",(0,r.jsx)(n.code,{children:"<project-root>/src/commands"}),". Having all the commands in one place will help you to understand your application's capabilities at a glance."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-text",children:"<project-root>\n\u251c\u2500\u2500 src\n\u2502\xa0\xa0 \u251c\u2500\u2500 commands <------ put them here\n\u2502\xa0\xa0 \u251c\u2500\u2500 common\n\u2502\xa0\xa0 \u251c\u2500\u2500 config\n\u2502\xa0\xa0 \u251c\u2500\u2500 entities\n\u2502\xa0\xa0 \u251c\u2500\u2500 events\n\u2502\xa0\xa0 \u251c\u2500\u2500 index.ts\n\u2502\xa0\xa0 \u2514\u2500\u2500 read-models\n"})})]})}function h(e={}){const{wrapper:n}={...(0,a.a)(),...e.components};return n?(0,r.jsx)(n,{...e,children:(0,r.jsx)(m,{...e})}):m(e)}},5163:(e,n,t)=>{t.d(n,{Z:()=>o});t(7294);const r={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var a=t(5893);function o(e){let{children:n}=e;return(0,a.jsxs)("div",{className:r.terminalWindow,children:[(0,a.jsx)("div",{className:r.terminalWindowHeader,children:(0,a.jsxs)("div",{className:r.buttons,children:[(0,a.jsx)("span",{className:r.dot,style:{background:"#f25f58"}}),(0,a.jsx)("span",{className:r.dot,style:{background:"#fbbe3c"}}),(0,a.jsx)("span",{className:r.dot,style:{background:"#58cb42"}})]})}),(0,a.jsx)("div",{className:r.terminalWindowBody,children:n})]})}},1151:(e,n,t)=>{t.d(n,{Z:()=>s,a:()=>i});var r=t(7294);const a={},o=r.createContext(a);function i(e){const n=r.useContext(o);return r.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function s(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:i(e.components),r.createElement(o.Provider,{value:n},e.children)}}}]);