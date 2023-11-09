"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5033],{3905:(e,t,n)=>{n.d(t,{Zo:()=>d,kt:()=>h});var r=n(7294);function s(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function o(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){s(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function a(e,t){if(null==e)return{};var n,r,s=function(e,t){if(null==e)return{};var n,r,s={},i=Object.keys(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||(s[n]=e[n]);return s}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(s[n]=e[n])}return s}var l=r.createContext({}),c=function(e){var t=r.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):o(o({},t),e)),n},d=function(e){var t=c(e.components);return r.createElement(l.Provider,{value:t},e.children)},p={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},u=r.forwardRef((function(e,t){var n=e.components,s=e.mdxType,i=e.originalType,l=e.parentName,d=a(e,["components","mdxType","originalType","parentName"]),u=c(n),h=s,m=u["".concat(l,".").concat(h)]||u[h]||p[h]||i;return n?r.createElement(m,o(o({ref:t},d),{},{components:n})):r.createElement(m,o({ref:t},d))}));function h(e,t){var n=arguments,s=t&&t.mdxType;if("string"==typeof e||s){var i=n.length,o=new Array(i);o[0]=u;var a={};for(var l in t)hasOwnProperty.call(t,l)&&(a[l]=t[l]);a.originalType=e,a.mdxType="string"==typeof e?e:s,o[1]=a;for(var c=2;c<i;c++)o[c]=n[c];return r.createElement.apply(null,o)}return r.createElement.apply(null,n)}u.displayName="MDXCreateElement"},1796:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>o,default:()=>p,frontMatter:()=>i,metadata:()=>a,toc:()=>c});var r=n(7462),s=(n(7294),n(3905));const i={},o="Advanced uses of the Register object",a={unversionedId:"going-deeper/register",id:"going-deeper/register",title:"Advanced uses of the Register object",description:"The Register object is a built-in object that is automatically injected by the framework into all command or event handlers to let users interact with the execution context. It can be used for a variety of purposes, including:",source:"@site/docs/10_going-deeper/register.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/register",permalink:"/going-deeper/register",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/register.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1699531616,formattedLastUpdatedAt:"Nov 9, 2023",frontMatter:{},sidebar:"docs",previous:{title:"Environments",permalink:"/going-deeper/environment-configuration"},next:{title:"Configuring Infrastructure Providers",permalink:"/going-deeper/infrastructure-providers"}},l={},c=[{value:"Registering events",id:"registering-events",level:2},{value:"Manually flush the events",id:"manually-flush-the-events",level:2},{value:"Access the current signed in user",id:"access-the-current-signed-in-user",level:2},{value:"Command-specific features",id:"command-specific-features",level:2},{value:"Access the request context",id:"access-the-request-context",level:3},{value:"Alter the HTTP response headers",id:"alter-the-http-response-headers",level:3}],d={toc:c};function p(e){let{components:t,...n}=e;return(0,s.kt)("wrapper",(0,r.Z)({},d,n,{components:t,mdxType:"MDXLayout"}),(0,s.kt)("h1",{id:"advanced-uses-of-the-register-object"},"Advanced uses of the Register object"),(0,s.kt)("p",null,"The Register object is a built-in object that is automatically injected by the framework into all command or event handlers to let users interact with the execution context. It can be used for a variety of purposes, including:"),(0,s.kt)("ul",null,(0,s.kt)("li",{parentName:"ul"},"Registering events to be emitted at the end of the command or event handler"),(0,s.kt)("li",{parentName:"ul"},"Manually flush the events to be persisted synchronously to the event store"),(0,s.kt)("li",{parentName:"ul"},"Access the current signed in user, their roles and other claims included in their JWT token"),(0,s.kt)("li",{parentName:"ul"},"In a command: Access the request context or alter the HTTP response headers")),(0,s.kt)("h2",{id:"registering-events"},"Registering events"),(0,s.kt)("p",null,"When handling a command or event, you can use the Register object to register one or more events that will be emitted when the command or event handler is completed. Events are registered using the ",(0,s.kt)("inlineCode",{parentName:"p"},"register.events()")," method, which takes one or more events as arguments. For example:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(new OrderConfirmed(this.orderID))\n  // Do more work...\n}\n")),(0,s.kt)("p",null,"In this example, we're registering an OrderConfirmed event to be persisted to the event store when the handler finishes. You can also register multiple events by passing them as separate arguments to the register.events() method:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(\n    new OrderConfirmed(this.orderID),\n    new OrderShipped(this.orderID)\n  )\n  // Do more work...\n}\n")),(0,s.kt)("p",null,"It's worth noting that events registered with ",(0,s.kt)("inlineCode",{parentName:"p"},"register.events()")," aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes executing. To force the events to be persisted immediately, you can call the ",(0,s.kt)("inlineCode",{parentName:"p"},"register.flush()")," method that is described in the next section."),(0,s.kt)("h2",{id:"manually-flush-the-events"},"Manually flush the events"),(0,s.kt)("p",null,"As mentioned in the previous section, events registered with ",(0,s.kt)("inlineCode",{parentName:"p"},"register.events()")," aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes its execution, but this doesn't work in all situations, sometimes it's useful to store partial updates of a longer process, and some scenarios could accept partial successes. To force the events to be persisted and wait for the database to confirm the write, you can use the ",(0,s.kt)("inlineCode",{parentName:"p"},"register.flush()")," method."),(0,s.kt)("p",null,"The ",(0,s.kt)("inlineCode",{parentName:"p"},"register.flush()")," method takes no arguments and returns a promise that resolves when the events have been successfully persisted to the event store. For example:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(new OrderConfirmed(this.orderID))\n  await register.flush()\n  const mailID = await sendConfirmationEmail(this.orderID)\n  register.events(new MailSent(this.orderID, mailID))\n  // Do more work...\n}\n")),(0,s.kt)("p",null,"In this example, we're calling ",(0,s.kt)("inlineCode",{parentName:"p"},"register.flush()")," after registering an ",(0,s.kt)("inlineCode",{parentName:"p"},"OrderConfirmed")," event to ensure that it's persisted to the event store before continuing with the rest of the handler logic. In this way, even if an error happens while sending the confirmation email, the order will be persisted."),(0,s.kt)("h2",{id:"access-the-current-signed-in-user"},"Access the current signed in user"),(0,s.kt)("p",null,"When handling a command or event, you can use the injected ",(0,s.kt)("inlineCode",{parentName:"p"},"Register")," object to access the currently signed-in user as well as any metadata included in their JWT token like their roles or other claims (the specific claims will depend on the specific auth provider used). To do this, you can use the ",(0,s.kt)("inlineCode",{parentName:"p"},"currentUser")," property. This property is an instance of the ",(0,s.kt)("inlineCode",{parentName:"p"},"UserEnvelope")," class, which has the following properties:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"export interface UserEnvelope {\n  id?: string // An optional identifier of the user\n  username: string // The unique username of the current user\n  roles: Array<string> // The list of role names assigned to this user\n  claims: Record<string, unknown> // An object containing the claims included in the body of the JWT token\n  header?: Record<string, unknown> // An object containing the headers of the JWT token for further verification\n}\n")),(0,s.kt)("p",null,"For example, to access the username of the currently signed-in user, you can use the ",(0,s.kt)("inlineCode",{parentName:"p"},"currentUser.username")," property:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"public async handle(register: Register): Promise<void> {\n  console.log(`The currently signed-in user is ${register.currentUser?.username}`)\n}\n\n// Output: The currently signed-in user is john.doe\n")),(0,s.kt)("h2",{id:"command-specific-features"},"Command-specific features"),(0,s.kt)("p",null,"The command handlers are executed as part of a GraphQL mutation request, so they have access to a few additional features that are specific to commands that can be used to access the request context or alter the HTTP response headers."),(0,s.kt)("h3",{id:"access-the-request-context"},"Access the request context"),(0,s.kt)("p",null,"The request context is injected in the command handler as part of the register command and you can access it using the ",(0,s.kt)("inlineCode",{parentName:"p"},"context")," property. This property is an instance of the ",(0,s.kt)("inlineCode",{parentName:"p"},"ContextEnvelope")," interface, which has the following properties:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"export interface ContextEnvelope {\n  /** Decoded request header and body */\n  request: {\n    headers: unknown\n    body: unknown\n  }\n  /** Provider-dependent raw request context object */\n  rawContext: unknown\n}\n")),(0,s.kt)("p",null,"The ",(0,s.kt)("inlineCode",{parentName:"p"},"request")," property exposes a normalized version of the request headers and body that can be used regardless the provider. We recommend using this property instead of the ",(0,s.kt)("inlineCode",{parentName:"p"},"rawContext")," property, as it will be more portable across providers."),(0,s.kt)("p",null,"The ",(0,s.kt)("inlineCode",{parentName:"p"},"rawContext")," property exposes the full raw request context as it comes in the original request, so it will depend on the underlying provider used. For instance, in AWS, it will be ",(0,s.kt)("a",{parentName:"p",href:"https://docs.aws.amazon.com/lambda/latest/dg/nodejs-context.html"},"a lambda context object"),", while in Azure it will be ",(0,s.kt)("a",{parentName:"p",href:"https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-node#context-object"},"an Azure Functions context object"),"."),(0,s.kt)("h3",{id:"alter-the-http-response-headers"},"Alter the HTTP response headers"),(0,s.kt)("p",null,"Finally, you can use the ",(0,s.kt)("inlineCode",{parentName:"p"},"responseHeaders")," property to alter the HTTP response headers that will be sent back to the client. This property is a plain Typescript object which is initialized with the default headers. You can add, remove or modify any of the headers by using the standard object methods:"),(0,s.kt)("pre",null,(0,s.kt)("code",{parentName:"pre",className:"language-typescript"},"public async handle(register: Register): Promise<void> {\n  register.responseHeaders['X-My-Header'] = 'My custom header'\n  register.responseHeaders['X-My-Other-Header'] = 'My other custom header'\n  delete register.responseHeaders['X-My-Other-Header']\n}\n")))}p.isMDXComponent=!0}}]);