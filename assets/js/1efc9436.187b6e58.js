"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[2126],{1829:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>a,default:()=>u,frontMatter:()=>o,metadata:()=>d,toc:()=>l});var i=n(5893),r=n(1151),s=n(5163);const o={},a="Entity",d={id:"architecture/entity",title:"Entity",description:"If events are the source of truth of your application, entities are the current state of your application. For example, if you have an application that allows users to create bank accounts, the events would be something like AccountCreated, MoneyDeposited, MoneyWithdrawn, etc. But the entities would be the BankAccount themselves, with the current balance, owner, etc.",source:"@site/docs/03_architecture/05_entity.mdx",sourceDirName:"03_architecture",slug:"/architecture/entity",permalink:"/architecture/entity",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/05_entity.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1724963259,formattedLastUpdatedAt:"Aug 29, 2024",sidebarPosition:5,frontMatter:{},sidebar:"docs",previous:{title:"Event handler",permalink:"/architecture/event-handler"},next:{title:"Read model",permalink:"/architecture/read-model"}},c={},l=[{value:"Creating entities",id:"creating-entities",level:2},{value:"Declaring an entity",id:"declaring-an-entity",level:2},{value:"The reduce function",id:"the-reduce-function",level:2},{value:"Reducing multiple events",id:"reducing-multiple-events",level:3},{value:"Eventual Consistency",id:"eventual-consistency",level:3},{value:"Entity ID",id:"entity-id",level:2},{value:"Entities naming convention",id:"entities-naming-convention",level:2}];function h(e){const t={a:"a",admonition:"admonition",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",img:"img",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,r.a)(),...e.components};return(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)(t.h1,{id:"entity",children:"Entity"}),"\n",(0,i.jsxs)(t.p,{children:["If events are the ",(0,i.jsx)(t.em,{children:"source of truth"})," of your application, entities are the ",(0,i.jsx)(t.em,{children:"current state"})," of your application. For example, if you have an application that allows users to create bank accounts, the events would be something like ",(0,i.jsx)(t.code,{children:"AccountCreated"}),", ",(0,i.jsx)(t.code,{children:"MoneyDeposited"}),", ",(0,i.jsx)(t.code,{children:"MoneyWithdrawn"}),", etc. But the entities would be the ",(0,i.jsx)(t.code,{children:"BankAccount"})," themselves, with the current balance, owner, etc."]}),"\n",(0,i.jsxs)(t.p,{children:["Entities are created by ",(0,i.jsx)(t.em,{children:"reducing"})," the whole event stream. Booster generates entities on the fly, so you don't have to worry about their creation. However, you must define them in order to instruct Booster how to generate them."]}),"\n",(0,i.jsx)(t.admonition,{type:"info",children:(0,i.jsx)(t.p,{children:"Under the hood, Booster stores snapshots of the entities in order to reduce the load on the event store. That way, Booster doesn't have to reduce the whole event stream whenever the current state of an entity is needed."})}),"\n",(0,i.jsx)(t.h2,{id:"creating-entities",children:"Creating entities"}),"\n",(0,i.jsx)(t.p,{children:"The Booster CLI will help you to create new entities. You just need to run the following command and the CLI will generate all the boilerplate for you:"}),"\n",(0,i.jsx)(s.Z,{children:(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-shell",children:"boost new:entity Product --fields displayName:string description:string price:Money\n"})})}),"\n",(0,i.jsxs)(t.p,{children:["This will generate a new file called ",(0,i.jsx)(t.code,{children:"product.ts"})," in the ",(0,i.jsx)(t.code,{children:"src/entities"})," directory. You can also create the file manually, but you will need to create the class and decorate it, so we recommend using the CLI."]}),"\n",(0,i.jsx)(t.h2,{id:"declaring-an-entity",children:"Declaring an entity"}),"\n",(0,i.jsxs)(t.p,{children:["To declare an entity in Booster, you must define a class decorated with the ",(0,i.jsx)(t.code,{children:"@Entity"})," decorator. Inside of the class, you must define a constructor with all the fields you want to have in your entity."]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/entities/entity-name.ts"',children:"@Entity\nexport class EntityName {\n  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeOtherType /* as many fields as needed */) {}\n}\n"})}),"\n",(0,i.jsx)(t.h2,{id:"the-reduce-function",children:"The reduce function"}),"\n",(0,i.jsxs)(t.p,{children:["In order to tell Booster how to reduce the events, you must define a static method decorated with the ",(0,i.jsx)(t.code,{children:"@Reduces"})," decorator. This method will be called by the framework every time an event of the specified type is emitted. The reducer method must return a new entity instance with the current state of the entity."]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/entities/entity-name.ts"',children:"@Entity\nexport class EntityName {\n  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeOtherType /* as many fields as needed */) {}\n\n  // highlight-start\n  @Reduces(SomeEvent)\n  public static reduceSomeEvent(event: SomeEvent, currentEntityState?: EntityName): EntityName {\n    /* Return a new entity based on the current one */\n  }\n  // highlight-end\n}\n"})}),"\n",(0,i.jsx)(t.p,{children:"The reducer method receives two parameters:"}),"\n",(0,i.jsxs)(t.ul,{children:["\n",(0,i.jsxs)(t.li,{children:[(0,i.jsx)(t.code,{children:"event"})," - The event object that triggered the reducer"]}),"\n",(0,i.jsxs)(t.li,{children:[(0,i.jsx)(t.code,{children:"currentEntity?"})," - The current state of the entity instance that the event belongs to if it exists. ",(0,i.jsx)(t.strong,{children:"This parameter is optional"})," and will be ",(0,i.jsx)(t.code,{children:"undefined"})," if the entity doesn't exist yet (For example, when you process a ",(0,i.jsx)(t.code,{children:"ProductCreated"})," event that will generate the first version of a ",(0,i.jsx)(t.code,{children:"Product"})," entity)."]}),"\n"]}),"\n",(0,i.jsx)(t.h3,{id:"reducing-multiple-events",children:"Reducing multiple events"}),"\n",(0,i.jsxs)(t.p,{children:["You can define as many reducer methods as you want, each one for a different event type. For example, if you have a ",(0,i.jsx)(t.code,{children:"Cart"})," entity, you could define a reducer for ",(0,i.jsx)(t.code,{children:"ProductAdded"})," events and another one for ",(0,i.jsx)(t.code,{children:"ProductRemoved"})," events."]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/entities/cart.ts"',children:"@Entity\nexport class Cart {\n  public constructor(readonly items: Array<CartItem>) {}\n\n  @Reduces(ProductAdded)\n  public static reduceProductAdded(event: ProductAdded, currentCart?: Cart): Cart {\n    const newItems = addToCart(event.item, currentCart)\n    return new Cart(newItems)\n  }\n\n  @Reduces(ProductRemoved)\n  public static reduceProductRemoved(event: ProductRemoved, currentCart?: Cart): Cart {\n    const newItems = removeFromCart(event.item, currentCart)\n    return new Cart(newItems)\n  }\n}\n"})}),"\n",(0,i.jsx)(t.admonition,{type:"tip",children:(0,i.jsxs)(t.p,{children:["It's highly recommended to ",(0,i.jsx)(t.strong,{children:"keep your reducer functions pure"}),", which means that you should be able to produce the new entity version by just looking at the event and the current entity state. You should avoid calling third party services, reading or writing to a database, or changing any external state."]})}),"\n",(0,i.jsxs)(t.p,{children:["There could be a lot of events being reduced concurrently among many entities, but, ",(0,i.jsx)(t.strong,{children:"for a specific entity instance, the events order is preserved"}),". This means that while one event is being reduced, all other events of any kind ",(0,i.jsx)(t.em,{children:"that belong to the same entity instance"})," will be waiting in a queue until the previous reducer has finished. This is how Booster guarantees that the entity state is consistent."]}),"\n",(0,i.jsx)(t.p,{children:(0,i.jsx)(t.img,{alt:"reducer process gif",src:n(5876).Z+"",width:"1208",height:"638"})}),"\n",(0,i.jsx)(t.h3,{id:"eventual-consistency",children:"Eventual Consistency"}),"\n",(0,i.jsxs)(t.p,{children:["Additionally, due to the event driven and async nature of Booster, your data might not be instantly updated. Booster will consume the commands, generate events, and ",(0,i.jsx)(t.em,{children:"eventually"})," generate the entities. Most of the time this is not perceivable, but under huge loads, it could be noticed."]}),"\n",(0,i.jsxs)(t.p,{children:["This property is called ",(0,i.jsx)(t.a,{href:"https://en.wikipedia.org/wiki/Eventual_consistency",children:"Eventual Consistency"}),", and it is a trade-off to have high availability for extreme situations, where other systems might simply fail."]}),"\n",(0,i.jsx)(t.h2,{id:"entity-id",children:"Entity ID"}),"\n",(0,i.jsxs)(t.p,{children:["In order to identify each entity instance, you must define an ",(0,i.jsx)(t.code,{children:"id"})," field on each entity. This field will be used by the framework to identify the entity instance. If the value of the ",(0,i.jsx)(t.code,{children:"id"})," field matches the value returned by the ",(0,i.jsxs)(t.a,{href:"event#events-and-entities",children:[(0,i.jsx)(t.code,{children:"entityID()"})," method"]})," of an Event, the framework will consider that the event belongs to that entity instance."]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-typescript",metastring:'title="src/entities/entity-name.ts"',children:"@Entity\nexport class EntityName {\n  public constructor(\n    // highlight-next-line\n    readonly id: UUID,\n    readonly fieldA: SomeType,\n    readonly fieldB: SomeOtherType /* as many fields as needed */\n  ) {}\n\n  @Reduces(SomeEvent)\n  public static reduceSomeEvent(event: SomeEvent, currentEntityState?: EntityName): EntityName {\n    /* Return a new entity based on the current one */\n  }\n}\n"})}),"\n",(0,i.jsx)(t.admonition,{type:"tip",children:(0,i.jsxs)(t.p,{children:["We recommend you to use the ",(0,i.jsx)(t.code,{children:"UUID"})," type for the ",(0,i.jsx)(t.code,{children:"id"})," field. You can generate a new ",(0,i.jsx)(t.code,{children:"UUID"})," value by calling the ",(0,i.jsx)(t.code,{children:"UUID.generate()"})," method already provided by the framework."]})}),"\n",(0,i.jsx)(t.h2,{id:"entities-naming-convention",children:"Entities naming convention"}),"\n",(0,i.jsx)(t.p,{children:"Entities are a representation of your application state in a specific moment, so name them as closely to your domain objects as possible. Typical entity names are nouns that might appear when you think about your app. In an e-commerce application, some entities would be:"}),"\n",(0,i.jsxs)(t.ul,{children:["\n",(0,i.jsx)(t.li,{children:"Cart"}),"\n",(0,i.jsx)(t.li,{children:"Product"}),"\n",(0,i.jsx)(t.li,{children:"UserProfile"}),"\n",(0,i.jsx)(t.li,{children:"Order"}),"\n",(0,i.jsx)(t.li,{children:"Address"}),"\n",(0,i.jsx)(t.li,{children:"PaymentMethod"}),"\n",(0,i.jsx)(t.li,{children:"Stock"}),"\n"]}),"\n",(0,i.jsxs)(t.p,{children:["Entities live within the entities directory of the project source: ",(0,i.jsx)(t.code,{children:"<project-root>/src/entities"}),"."]}),"\n",(0,i.jsx)(t.pre,{children:(0,i.jsx)(t.code,{className:"language-text",children:"<project-root>\n\u251c\u2500\u2500 src\n\u2502   \u251c\u2500\u2500 commands\n\u2502   \u251c\u2500\u2500 common\n\u2502   \u251c\u2500\u2500 config\n\u2502   \u251c\u2500\u2500 entities <------ put them here\n\u2502   \u251c\u2500\u2500 events\n\u2502   \u251c\u2500\u2500 index.ts\n\u2502   \u2514\u2500\u2500 read-models\n"})})]})}function u(e={}){const{wrapper:t}={...(0,r.a)(),...e.components};return t?(0,i.jsx)(t,{...e,children:(0,i.jsx)(h,{...e})}):h(e)}},5163:(e,t,n)=>{n.d(t,{Z:()=>s});n(7294);const i={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var r=n(5893);function s(e){let{children:t}=e;return(0,r.jsxs)("div",{className:i.terminalWindow,children:[(0,r.jsx)("div",{className:i.terminalWindowHeader,children:(0,r.jsxs)("div",{className:i.buttons,children:[(0,r.jsx)("span",{className:i.dot,style:{background:"#f25f58"}}),(0,r.jsx)("span",{className:i.dot,style:{background:"#fbbe3c"}}),(0,r.jsx)("span",{className:i.dot,style:{background:"#58cb42"}})]})}),(0,r.jsx)("div",{className:i.terminalWindowBody,children:t})]})}},5876:(e,t,n)=>{n.d(t,{Z:()=>i});const i=n.p+"assets/images/reducer-faf967cd976ea38d84e14551aa3af383.gif"},1151:(e,t,n)=>{n.d(t,{Z:()=>a,a:()=>o});var i=n(7294);const r={},s=i.createContext(r);function o(e){const t=i.useContext(s);return i.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function a(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(r):e.components||r:o(e.components),i.createElement(s.Provider,{value:t},e.children)}}}]);