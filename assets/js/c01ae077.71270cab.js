"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5941],{5447:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>r,default:()=>h,frontMatter:()=>o,metadata:()=>a,toc:()=>c});var s=n(5893),i=n(1151);const o={},r="Introduction",a={id:"introduction",title:"Introduction",description:"Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something. \u2014 Robert A. Heinlein",source:"@site/docs/01_introduction.md",sourceDirName:".",slug:"/introduction",permalink:"/introduction",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/01_introduction.md",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1722356865,formattedLastUpdatedAt:"Jul 30, 2024",sidebarPosition:1,frontMatter:{},sidebar:"docs",previous:{title:"Ask about Booster Framework",permalink:"/"},next:{title:"Getting Started",permalink:"/category/getting-started"}},l={},c=[{value:"What is Booster?",id:"what-is-booster",level:2},{value:"Booster Principles",id:"booster-principles",level:2},{value:"Why use Booster",id:"why-use-booster",level:2}];function d(e){const t={a:"a",blockquote:"blockquote",em:"em",h1:"h1",h2:"h2",li:"li",p:"p",strong:"strong",ul:"ul",...(0,i.a)(),...e.components};return(0,s.jsxs)(s.Fragment,{children:[(0,s.jsx)(t.h1,{id:"introduction",children:"Introduction"}),"\n",(0,s.jsxs)(t.blockquote,{children:["\n",(0,s.jsxs)(t.p,{children:[(0,s.jsx)(t.em,{children:"Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something."})," \u2014 ",(0,s.jsx)(t.a,{href:"https://en.wikipedia.org/wiki/Robert_A._Heinlein",children:"Robert A. Heinlein"})]}),"\n"]}),"\n",(0,s.jsx)(t.h2,{id:"what-is-booster",children:"What is Booster?"}),"\n",(0,s.jsx)(t.p,{children:"Booster is the fastest way to create an application in the cloud. It is a new kind of framework to build scalable and reliable systems easier, reimagining the software development experience to maximize your team\u2019s speed and reduce friction on every level."}),"\n",(0,s.jsx)(t.p,{children:"Booster follows an Event-Driven and a Domain-Driven Design approach in which you define your application in terms that are understandable by anyone in your company. From a bird\u2019s eye view, your project is organized into:"}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Commands"}),": Define what a user can request from the system (i.e: Add an item to the cart)"]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Queries"}),": Define what a user can get from the system (i.e: Get cart items)"]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Events"}),": Simple records of facts (i.e: User X added item Y to the cart Z)"]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Entities"}),": Data about the things that the people in your company talk about (i.e: Orders, Customers, etc.)"]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Handlers"}),": Code that processes commands, reacts to events to trigger other actions, or update the entities after new events happen."]}),"\n"]}),"\n",(0,s.jsx)(t.p,{children:"Events are the cornerstone of a Booster application, and that\u2019s why we say that Booster is an event-driven framework. Events bring us many of the differentiating characteristics of Booster:"}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Real-time"}),": Events can trigger other actions when they\u2019re created, and updates can be pushed to connected clients without extra requests."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"High data resiliency"}),": Events are stored by default in an append-only database, so the data is never lost and it\u2019s possible to recover any previous state of the system."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Scalable by nature"}),": Dependencies only happen at data level, so Booster apps can ingest more data without waiting for other operatons to complete. Low coupling also makes it easier to evolve the code without affecting other parts of the system."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Asynchronous"}),": Your users won't need to wait for your system to process the whole operation before continuing using it."]}),"\n"]}),"\n",(0,s.jsx)(t.p,{children:"Before Booster, building an event-driven system with the mentioned characteristics required huge investments in hiring engineers with the needed expertise. Booster packs this expertise, acquired from real-case scenarios in high-scale companies, into a very simple tool that handles with the hard parts for you, even provisioning the infrastructure!"}),"\n",(0,s.jsx)(t.p,{children:"We have redesigned the whole developer experience from scratch, taking advantage of the advanced TypeScript type system and Serverless technologies to go from project generation to a production-ready application in the cloud which provides a real-time GraphQL API that can ingest thousands of concurrent users in a matter of minutes."}),"\n",(0,s.jsx)(t.p,{children:"Booster's ultimate goal is making developer's lives easier, fulfilling the dream of writing code in a domain-driven way that eases communications for the whole team, without caring about how anything else is done at the infrastructure level!"}),"\n",(0,s.jsx)(t.h2,{id:"booster-principles",children:"Booster Principles"}),"\n",(0,s.jsx)(t.p,{children:"Booster enhances developers' productivity by focusing only on business logic. Write your code, provide your credentials and let Booster do the rest. Booster takes a holistic and highly-opinionated approach at many levels:"}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Focus on business value"}),": The only code that makes sense is the code that makes your application different from any other."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Convention over configuration"}),": All the supporting code and configuration that is similar in all applications should be out of programmers\u2019 sight."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Truly Serverless"}),": Why go Serverless to avoid managing infrastructure when you can implicitly infer your Serverless architecture from your code and not even deal with that?"]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Effective Multicloud"}),": Booster design makes it possible to run the same application in any of the supported cloud providers with no code changes in your application."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Scale smoothly"}),": The code you write to handle your first 100 users will still work to handle your first million. You won't need to rewrite your application when it succeeds."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Event-source and CQRS"}),": Our world is event-driven, businesses are event-driven, and modern software maps better to reality when it\u2019s event-driven."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Principle of Abstraction"}),": Building an application is hard enough to have to deal with recurring low-level details like SQL, API design, or authentication mechanisms, so we tend to build more semantic abstractions on top of them."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Real-time first"}),": Client applications must be able to react to events happening in the backend and notice data changes."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Extensible"}),": Rockets are what we call plugins in the Booster ecosystem. A rocket is a regular node package that works out of the box and provides new end-to-end abstractions, supports new cloud services, or pre-built functionalities that you can install in your project."]}),"\n"]}),"\n",(0,s.jsx)(t.h2,{id:"why-use-booster",children:"Why use Booster"}),"\n",(0,s.jsxs)(t.p,{children:["What does ",(0,s.jsx)(t.em,{children:"Booster"})," boost? Your team\u2019s productivity. Not just because it helps you work faster, but because it makes you worry about fewer buttons and switches. We aim to solve major productivity sinks for developers like designing the right cloud infrastructure, writing APIs or dealing with ORMs."]}),"\n",(0,s.jsx)(t.p,{children:"Booster will fit like a glove in applications that are naturally event-driven like commerce applications (retail, e-commerce, omnichannel applications, warehouse management, etc.), business applications or communication systems, but it's a general-purpose framework that has several advantages over other solutions:"}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Faster time-to-market"}),": Booster can deploy your application to a production-ready environment from minute one, without complicated configurations or needing to invest any effort to design it. In addition to that, it features a set of code generators to help developers build the project scaffolding faster and focus on actual business code in a matter of seconds instead of dealing with complicated framework folklore."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Write less code"}),": Booster conventions and abstractions require less code to implement the same features. This not only speeds up development but combined with clear architecture guidelines also makes Booster projects easier to understand, iterate, and maintain."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Benefit from Typescript's advantages"}),": Typescript's type system provides an important security layer that helps developers make sure the code they write is the code they meant to write, making Booster apps more reliable and less error-prone."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"All the advantages of Microservices, none of its cons"}),": Microservices are a great way to deal with code complexity, at least on paper. Services are isolated and can scale independently, and different teams can work independently, but that usually comes with a con: interfaces between services introduce huge challenges like delays, hard to solve cyclic dependencies, or deployment errors. In Booster, every handler function works as an independent microservice, it scales separately in its own lambda function, and there are no direct dependencies between them, all communication happens asynchronously via events, and all the infrastructure is compiled, type-checked and deployed atomically to avoid issues."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"All the advantages of Serverless, without needing a degree in cloud technologies"}),": Serverless technologies are amazing and have made a project like Booster possible, but they're relatively new technologies, and while day after day new tools appear to make them easier, the learning curve is still quite steep. With Booster you'll take advantage of Serverless\u2019 main selling points of high scalability and reduced hosting costs, without having to learn every detail from minute one."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Event-sourcing by default"}),": Booster keeps all incremental data changes as events, indefinitely. This means that any previous state of the system can be recreated and replayed at any moment, enabling a whole world of possibilities for troubleshooting and auditing, syncing environments or performing tests and simulations."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Booster makes it easy to build enterprise-grade applications"}),": Implementing an event-sourcing system from scratch is a challenging exercise that usually requires highly specialized experts. There are some technical challenges like eventual consistency, message ordering, and snapshot building. Booster takes care of all of that and more for you, lowering the curve for people that are starting and making expert lives easier."]}),"\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.strong,{children:"Choose your application cloud and avoid vendor lock-in:"})," Booster provides a highly decoupled architecture that enables the possibility of integrating with ease new providers with different specifications, including a custom Multi-cloud provider, without affecting the framework specification."]}),"\n"]})]})}function h(e={}){const{wrapper:t}={...(0,i.a)(),...e.components};return t?(0,s.jsx)(t,{...e,children:(0,s.jsx)(d,{...e})}):d(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>a,a:()=>r});var s=n(7294);const i={},o=s.createContext(i);function r(e){const t=s.useContext(o);return s.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function a(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(i):e.components||i:r(e.components),s.createElement(o.Provider,{value:t},e.children)}}}]);