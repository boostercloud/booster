"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[3530],{9425:(e,r,t)=>{t.r(r),t.d(r,{assets:()=>c,contentTitle:()=>s,default:()=>d,frontMatter:()=>i,metadata:()=>o,toc:()=>u});var n=t(5893),a=t(1151);const i={},s="Queries",o={id:"architecture/queries",title:"Queries",description:"ReadModels offer read operations over reduced events. On the other hand, Queries provide a way to do custom read operations.",source:"@site/docs/03_architecture/08_queries.mdx",sourceDirName:"03_architecture",slug:"/architecture/queries",permalink:"/architecture/queries",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/08_queries.mdx",tags:[],version:"current",lastUpdatedBy:"Nick Tchayka",lastUpdatedAt:1706121121,formattedLastUpdatedAt:"Jan 24, 2024",sidebarPosition:8,frontMatter:{},sidebar:"docs",previous:{title:"Notifications",permalink:"/architecture/notifications"},next:{title:"Features",permalink:"/category/features"}},c={},u=[{value:"Queries naming convention",id:"queries-naming-convention",level:2},{value:"Creating a query",id:"creating-a-query",level:2},{value:"The query handler function",id:"the-query-handler-function",level:2},{value:"Validating data",id:"validating-data",level:3},{value:"Throw an error",id:"throw-an-error",level:4},{value:"Registering events",id:"registering-events",level:3},{value:"Authorizing queries",id:"authorizing-queries",level:2},{value:"Querying",id:"querying",level:2}];function l(e){const r={a:"a",blockquote:"blockquote",code:"code",h1:"h1",h2:"h2",h3:"h3",h4:"h4",li:"li",p:"p",pre:"pre",ul:"ul",...(0,a.a)(),...e.components};return(0,n.jsxs)(n.Fragment,{children:[(0,n.jsx)(r.h1,{id:"queries",children:"Queries"}),"\n",(0,n.jsx)(r.p,{children:"ReadModels offer read operations over reduced events. On the other hand, Queries provide a way to do custom read operations."}),"\n",(0,n.jsxs)(r.p,{children:["Queries are classes decorated with the ",(0,n.jsx)(r.code,{children:"@Query"})," decorator that have a ",(0,n.jsx)(r.code,{children:"handle"})," method."]}),"\n",(0,n.jsx)(r.pre,{children:(0,n.jsx)(r.code,{className:"language-typescript",children:"import { Booster, NonExposed, Query } from '@boostercloud/framework-core'\nimport { QueryInfo, QueryInput, UserEnvelope, UUID } from '@boostercloud/framework-types'\nimport { Cart } from '../entities/cart'\nimport {\n  beforeHookQueryID,\n  beforeHookQueryMultiply,\n  queryHandlerErrorCartId,\n  queryHandlerErrorCartMessage,\n} from '../constants'\n\n@Query({\n  authorize: 'all',\n})\nexport class CartTotalQuantity {\n  public constructor(readonly cartId: UUID, @NonExposed readonly multiply: number) {}\n\n  public static async handle(query: CartTotalQuantity, queryInfo: QueryInfo): Promise<number> {\n    const cart = await Booster.entity(Cart, query.cartId)\n    if (!cart || !cart.cartItems || cart.cartItems.length === 0) {\n      return 0\n    }\n    return cart?.cartItems\n      .map((cartItem) => cartItem.quantity)\n      .reduce((accumulator, value) => {\n        return accumulator + value\n      }, 0)\n  }\n}\n"})}),"\n",(0,n.jsx)(r.h2,{id:"queries-naming-convention",children:"Queries naming convention"}),"\n",(0,n.jsxs)(r.p,{children:["We recommend use the ",(0,n.jsx)(r.code,{children:"Query"})," suffix in your queries name."]}),"\n",(0,n.jsxs)(r.p,{children:["Despite you can place your queries in any directory, we strongly recommend you to put them in ",(0,n.jsx)(r.code,{children:"<project-root>/src/queries"}),"."]}),"\n",(0,n.jsx)(r.pre,{children:(0,n.jsx)(r.code,{className:"language-text",children:"<project-root>\n\u251c\u2500\u2500 src\n\u2502\xa0\xa0 \u251c\u2500\u2500 commands\n\u2502\xa0\xa0 \u251c\u2500\u2500 common\n\u2502\xa0\xa0 \u251c\u2500\u2500 config\n\u2502\xa0\xa0 \u251c\u2500\u2500 entities\n\u2502\xa0\xa0 \u251c\u2500\u2500 read-models\n\u2502\xa0\xa0 \u251c\u2500\u2500 events\n\u2502\xa0\xa0 \u251c\u2500\u2500 queries      <------ put them here\n\u2502\xa0\xa0 \u2514\u2500\u2500 index.ts\n"})}),"\n",(0,n.jsx)(r.h2,{id:"creating-a-query",children:"Creating a query"}),"\n",(0,n.jsx)(r.p,{children:"The preferred way to create a query is by using the generator, e.g."}),"\n",(0,n.jsx)(r.pre,{children:(0,n.jsx)(r.code,{className:"language-shell",children:"boost new:query ItemsInCountry --fields country:string\n"})}),"\n",(0,n.jsxs)(r.p,{children:["The generator will create a Typescript class under the queries directory ",(0,n.jsx)(r.code,{children:"<project-root>/src/queries/items-in-country.ts"}),"."]}),"\n",(0,n.jsx)(r.p,{children:"Queries classes can also be created by hand and there are no restrictions. The structure of the data is totally open and can be as complex as you can manage in your projection functions."}),"\n",(0,n.jsx)(r.h2,{id:"the-query-handler-function",children:"The query handler function"}),"\n",(0,n.jsxs)(r.p,{children:["Each query class must have a method called ",(0,n.jsx)(r.code,{children:"handle"}),". This function is the command handler, and it will be called by the framework every time one instance of this query is submitted. Inside the handler you can run validations, return errors and query entities to make decisions."]}),"\n",(0,n.jsx)(r.p,{children:"Handler function receive a QueryInfo object to let users interact with the execution context. It can be used for a variety of purposes, including:"}),"\n",(0,n.jsxs)(r.ul,{children:["\n",(0,n.jsx)(r.li,{children:"Access the current signed in user, their roles and other claims included in their JWT token"}),"\n",(0,n.jsx)(r.li,{children:"Access the request context or alter the HTTP response headers"}),"\n"]}),"\n",(0,n.jsx)(r.h3,{id:"validating-data",children:"Validating data"}),"\n",(0,n.jsx)(r.p,{children:"Booster uses the typed nature of GraphQL to ensure that types are correct before reaching the handler, so you don't have to validate types."}),"\n",(0,n.jsx)(r.h4,{id:"throw-an-error",children:"Throw an error"}),"\n",(0,n.jsx)(r.p,{children:"There are still business rules to be checked before proceeding with a query. For example, a given number must be between a threshold or a string must match a regular expression. In that case, it is enough just to throw an error in the handler. Booster will use the error's message as the response to make it descriptive."}),"\n",(0,n.jsx)(r.h3,{id:"registering-events",children:"Registering events"}),"\n",(0,n.jsxs)(r.p,{children:["Within the query handler execution, it is not possible to register domain events. If you need to register events, then use a Command. For more details about events and the register parameter, see the ",(0,n.jsx)(r.a,{href:"/architecture/event",children:(0,n.jsx)(r.code,{children:"Events"})})," section."]}),"\n",(0,n.jsx)(r.h2,{id:"authorizing-queries",children:"Authorizing queries"}),"\n",(0,n.jsxs)(r.p,{children:["You can define who is authorized to access your queries. The Booster authorization feature is covered in ",(0,n.jsx)(r.a,{href:"/security/authentication",children:"the auth section"}),". So far, we have seen that you can make a query publicly accessible by authorizing ",(0,n.jsx)(r.code,{children:"'all'"})," to query it, or you can set specific roles providing an array of roles in this way: ",(0,n.jsx)(r.code,{children:"authorize: [Admin]"}),"."]}),"\n",(0,n.jsx)(r.h2,{id:"querying",children:"Querying"}),"\n",(0,n.jsxs)(r.p,{children:["For every query, Booster automatically creates the corresponding GraphQL query. For example, given this ",(0,n.jsx)(r.code,{children:"CartTotalQuantityQuery"}),":"]}),"\n",(0,n.jsx)(r.pre,{children:(0,n.jsx)(r.code,{className:"language-typescript",children:"@Query({\n  authorize: 'all',\n})\nexport class CartTotalQuantityQuery {\n  public constructor(readonly cartId: UUID) {}\n\n  public static async handle(query: CartTotalQuantity, queryInfo: QueryInfo): Promise<number> {\n    const cart = await Booster.entity(Cart, query.cartId)\n    if (!cart || !cart.cartItems || cart.cartItems.length === 0) {\n      return 0\n    }\n    return cart?.cartItems\n      .map((cartItem) => cartItem.quantity)\n      .reduce((accumulator, value) => {\n        return accumulator + value\n      }, 0)\n  }\n}\n"})}),"\n",(0,n.jsx)(r.p,{children:"You will get the following GraphQL query and subscriptions:"}),"\n",(0,n.jsx)(r.pre,{children:(0,n.jsx)(r.code,{className:"language-graphQL",children:"query CartTotalQuantityQuery($cartId: ID!): Float!\n"})}),"\n",(0,n.jsxs)(r.blockquote,{children:["\n",(0,n.jsx)(r.p,{children:"[!NOTE] Query subscriptions are not supported yet"}),"\n"]})]})}function d(e={}){const{wrapper:r}={...(0,a.a)(),...e.components};return r?(0,n.jsx)(r,{...e,children:(0,n.jsx)(l,{...e})}):l(e)}},1151:(e,r,t)=>{t.d(r,{Z:()=>o,a:()=>s});var n=t(7294);const a={},i=n.createContext(a);function s(e){const r=n.useContext(i);return n.useMemo((function(){return"function"==typeof e?e(r):{...r,...e}}),[r,e])}function o(e){let r;return r=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:s(e.components),n.createElement(i.Provider,{value:r},e.children)}}}]);