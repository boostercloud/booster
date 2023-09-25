"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5941],{3905:(e,t,n)=>{n.d(t,{Zo:()=>p,kt:()=>m});var a=n(7294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function o(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function i(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?o(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):o(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function s(e,t){if(null==e)return{};var n,a,r=function(e,t){if(null==e)return{};var n,a,r={},o=Object.keys(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var l=a.createContext({}),c=function(e){var t=a.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):i(i({},t),e)),n},p=function(e){var t=c(e.components);return a.createElement(l.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return a.createElement(a.Fragment,{},t)}},u=a.forwardRef((function(e,t){var n=e.components,r=e.mdxType,o=e.originalType,l=e.parentName,p=s(e,["components","mdxType","originalType","parentName"]),u=c(n),m=r,h=u["".concat(l,".").concat(m)]||u[m]||d[m]||o;return n?a.createElement(h,i(i({ref:t},p),{},{components:n})):a.createElement(h,i({ref:t},p))}));function m(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var o=n.length,i=new Array(o);i[0]=u;var s={};for(var l in t)hasOwnProperty.call(t,l)&&(s[l]=t[l]);s.originalType=e,s.mdxType="string"==typeof e?e:r,i[1]=s;for(var c=2;c<o;c++)i[c]=n[c];return a.createElement.apply(null,i)}return a.createElement.apply(null,n)}u.displayName="MDXCreateElement"},7050:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>i,default:()=>d,frontMatter:()=>o,metadata:()=>s,toc:()=>c});var a=n(7462),r=(n(7294),n(3905));const o={},i="Introduction",s={unversionedId:"introduction",id:"introduction",title:"Introduction",description:"Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something. \u2014 Robert A. Heinlein",source:"@site/docs/01_introduction.md",sourceDirName:".",slug:"/introduction",permalink:"/introduction",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/01_introduction.md",tags:[],version:"current",lastUpdatedBy:"Javier Toledo",lastUpdatedAt:1695655064,formattedLastUpdatedAt:"Sep 25, 2023",sidebarPosition:1,frontMatter:{},sidebar:"docs",previous:{title:"Ask about Booster Framework",permalink:"/"},next:{title:"Getting Started",permalink:"/category/getting-started"}},l={},c=[{value:"What is Booster?",id:"what-is-booster",level:2},{value:"Booster Principles",id:"booster-principles",level:2},{value:"Why use Booster",id:"why-use-booster",level:2}],p={toc:c};function d(e){let{components:t,...n}=e;return(0,r.kt)("wrapper",(0,a.Z)({},p,n,{components:t,mdxType:"MDXLayout"}),(0,r.kt)("h1",{id:"introduction"},"Introduction"),(0,r.kt)("blockquote",null,(0,r.kt)("p",{parentName:"blockquote"},(0,r.kt)("em",{parentName:"p"},"Progress isn't made by early risers. It's made by lazy men trying to find easier ways to do something.")," \u2014 ",(0,r.kt)("a",{parentName:"p",href:"https://en.wikipedia.org/wiki/Robert_A._Heinlein"},"Robert A. Heinlein"))),(0,r.kt)("h2",{id:"what-is-booster"},"What is Booster?"),(0,r.kt)("p",null,"Booster is the fastest way to create an application in the cloud. It is a new kind of framework to build scalable and reliable systems easier, reimagining the software development experience to maximize your team\u2019s speed and reduce friction on every level."),(0,r.kt)("p",null,"Booster follows an Event-Driven and a Domain-Driven Design approach in which you define your application in terms that are understandable by anyone in your company. From a bird\u2019s eye view, your project is organized into:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Commands"),": Define what a user can request from the system (i.e: Add an item to the cart)"),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Queries"),": Define what a user can get from the system (i.e: Get cart items)"),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Events"),": Simple records of facts (i.e: User X added item Y to the cart Z)"),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Entities"),": Data about the things that the people in your company talk about (i.e: Orders, Customers, etc.)"),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Handlers"),": Code that processes commands, reacts to events to trigger other actions, or update the entities after new events happen.")),(0,r.kt)("p",null,"Events are the cornerstone of a Booster application, and that\u2019s why we say that Booster is an event-driven framework. Events bring us many of the differentiating characteristics of Booster:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Real-time"),": Events can trigger other actions when they\u2019re created, and updates can be pushed to connected clients without extra requests."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"High data resiliency"),": Events are stored by default in an append-only database, so the data is never lost and it\u2019s possible to recover any previous state of the system."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Scalable by nature"),": Dependencies only happen at data level, so Booster apps can ingest more data without waiting for other operatons to complete. Low coupling also makes it easier to evolve the code without affecting other parts of the system."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Asynchronous"),": Your users won't need to wait for your system to process the whole operation before continuing using it.")),(0,r.kt)("p",null,"Before Booster, building an event-driven system with the mentioned characteristics required huge investments in hiring engineers with the needed expertise. Booster packs this expertise, acquired from real-case scenarios in high-scale companies, into a very simple tool that handles with the hard parts for you, even provisioning the infrastructure!"),(0,r.kt)("p",null,"We have redesigned the whole developer experience from scratch, taking advantage of the advanced TypeScript type system and Serverless technologies to go from project generation to a production-ready application in the cloud which provides a real-time GraphQL API that can ingest thousands of concurrent users in a matter of minutes."),(0,r.kt)("p",null,"Booster's ultimate goal is making developer's lives easier, fulfilling the dream of writing code in a domain-driven way that eases communications for the whole team, without caring about how anything else is done at the infrastructure level!"),(0,r.kt)("h2",{id:"booster-principles"},"Booster Principles"),(0,r.kt)("p",null,"Booster enhances developers' productivity by focusing only on business logic. Write your code, provide your credentials and let Booster do the rest. Booster takes a holistic and highly-opinionated approach at many levels:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Focus on business value"),": The only code that makes sense is the code that makes your application different from any other."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Convention over configuration"),": All the supporting code and configuration that is similar in all applications should be out of programmers\u2019 sight."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Truly Serverless"),": Why go Serverless to avoid managing infrastructure when you can implicitly infer your Serverless architecture from your code and not even deal with that?"),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Effective Multicloud"),": Booster design makes it possible to run the same application in AWS or Azure with no code changes in your application."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Scale smoothly"),": The code you write to handle your first 100 users will still work to handle your first million. You won't need to rewrite your application when it succeeds."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Event-source and CQRS"),": Our world is event-driven, businesses are event-driven, and modern software maps better to reality when it\u2019s event-driven."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Principle of Abstraction"),": Building an application is hard enough to have to deal with recurring low-level details like SQL, API design, or authentication mechanisms, so we tend to build more semantic abstractions on top of them."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Real-time first"),": Client applications must be able to react to events happening in the backend and notice data changes."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Extensible"),": Rockets are what we call plugins in the Booster ecosystem. A rocket is a regular node package that works out of the box and provides new end-to-end abstractions, supports new cloud services, or pre-built functionalities that you can install in your project.")),(0,r.kt)("h2",{id:"why-use-booster"},"Why use Booster"),(0,r.kt)("p",null,"What does ",(0,r.kt)("em",{parentName:"p"},"Booster")," boost? Your team\u2019s productivity. Not just because it helps you work faster, but because it makes you worry about fewer buttons and switches. We aim to solve major productivity sinks for developers like designing the right cloud infrastructure, writing APIs or dealing with ORMs."),(0,r.kt)("p",null,"Booster will fit like a glove in applications that are naturally event-driven like commerce applications (retail, e-commerce, omnichannel applications, warehouse management, etc.), business applications or communication systems, but it's a general-purpose framework that has several advantages over other solutions:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Faster time-to-market"),": Booster can deploy your application to a production-ready environment from minute one, without complicated configurations or needing to invest any effort to design it. In addition to that, it features a set of code generators to help developers build the project scaffolding faster and focus on actual business code in a matter of seconds instead of dealing with complicated framework folklore."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Write less code"),": Booster conventions and abstractions require less code to implement the same features. This not only speeds up development but combined with clear architecture guidelines also makes Booster projects easier to understand, iterate, and maintain."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Benefit from Typescript's advantages"),": Typescript's type system provides an important security layer that helps developers make sure the code they write is the code they meant to write, making Booster apps more reliable and less error-prone."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"All the advantages of Microservices, none of its cons"),": Microservices are a great way to deal with code complexity, at least on paper. Services are isolated and can scale independently, and different teams can work independently, but that usually comes with a con: interfaces between services introduce huge challenges like delays, hard to solve cyclic dependencies, or deployment errors. In Booster, every handler function works as an independent microservice, it scales separately in its own lambda function, and there are no direct dependencies between them, all communication happens asynchronously via events, and all the infrastructure is compiled, type-checked and deployed atomically to avoid issues."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"All the advantages of Serverless, without needing a degree in cloud technologies"),": Serverless technologies are amazing and have made a project like Booster possible, but they're relatively new technologies, and while day after day new tools appear to make them easier, the learning curve is still quite steep. With Booster you'll take advantage of Serverless\u2019 main selling points of high scalability and reduced hosting costs, without having to learn every detail from minute one."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Event-sourcing by default"),": Booster keeps all incremental data changes as events, indefinitely. This means that any previous state of the system can be recreated and replayed at any moment, enabling a whole world of possibilities for troubleshooting and auditing, syncing environments or performing tests and simulations."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Booster makes it easy to build enterprise-grade applications"),": Implementing an event-sourcing system from scratch is a challenging exercise that usually requires highly specialized experts. There are some technical challenges like eventual consistency, message ordering, and snapshot building. Booster takes care of all of that and more for you, lowering the curve for people that are starting and making expert lives easier."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("strong",{parentName:"li"},"Choose your application cloud and avoid vendor lock-in:")," Booster provides a highly decoupled architecture that enables the possibility of integrating with ease new providers with different specifications, including a custom Multi-cloud provider, without affecting the framework specification.")))}d.isMDXComponent=!0}}]);