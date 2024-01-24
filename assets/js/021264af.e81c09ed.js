"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[5033],{2784:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>o,default:()=>l,frontMatter:()=>i,metadata:()=>a,toc:()=>d});var r=n(5893),s=n(1151);const i={},o="Advanced uses of the Register object",a={id:"going-deeper/register",title:"Advanced uses of the Register object",description:"The Register object is a built-in object that is automatically injected by the framework into all command or event handlers to let users interact with the execution context. It can be used for a variety of purposes, including:",source:"@site/docs/10_going-deeper/register.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/register",permalink:"/going-deeper/register",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/register.mdx",tags:[],version:"current",lastUpdatedBy:"Nick Tchayka",lastUpdatedAt:1706121121,formattedLastUpdatedAt:"Jan 24, 2024",frontMatter:{},sidebar:"docs",previous:{title:"Environments",permalink:"/going-deeper/environment-configuration"},next:{title:"Configuring Infrastructure Providers",permalink:"/going-deeper/infrastructure-providers"}},c={},d=[{value:"Registering events",id:"registering-events",level:2},{value:"Manually flush the events",id:"manually-flush-the-events",level:2},{value:"Access the current signed in user",id:"access-the-current-signed-in-user",level:2},{value:"Command-specific features",id:"command-specific-features",level:2},{value:"Access the request context",id:"access-the-request-context",level:3},{value:"Alter the HTTP response headers",id:"alter-the-http-response-headers",level:3}];function h(e){const t={a:"a",code:"code",h1:"h1",h2:"h2",h3:"h3",li:"li",p:"p",pre:"pre",ul:"ul",...(0,s.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"advanced-uses-of-the-register-object",children:"Advanced uses of the Register object"}),"\n",(0,r.jsx)(t.p,{children:"The Register object is a built-in object that is automatically injected by the framework into all command or event handlers to let users interact with the execution context. It can be used for a variety of purposes, including:"}),"\n",(0,r.jsxs)(t.ul,{children:["\n",(0,r.jsx)(t.li,{children:"Registering events to be emitted at the end of the command or event handler"}),"\n",(0,r.jsx)(t.li,{children:"Manually flush the events to be persisted synchronously to the event store"}),"\n",(0,r.jsx)(t.li,{children:"Access the current signed in user, their roles and other claims included in their JWT token"}),"\n",(0,r.jsx)(t.li,{children:"In a command: Access the request context or alter the HTTP response headers"}),"\n"]}),"\n",(0,r.jsx)(t.h2,{id:"registering-events",children:"Registering events"}),"\n",(0,r.jsxs)(t.p,{children:["When handling a command or event, you can use the Register object to register one or more events that will be emitted when the command or event handler is completed. Events are registered using the ",(0,r.jsx)(t.code,{children:"register.events()"})," method, which takes one or more events as arguments. For example:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(new OrderConfirmed(this.orderID))\n  // Do more work...\n}\n"})}),"\n",(0,r.jsx)(t.p,{children:"In this example, we're registering an OrderConfirmed event to be persisted to the event store when the handler finishes. You can also register multiple events by passing them as separate arguments to the register.events() method:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(\n    new OrderConfirmed(this.orderID),\n    new OrderShipped(this.orderID)\n  )\n  // Do more work...\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["It's worth noting that events registered with ",(0,r.jsx)(t.code,{children:"register.events()"})," aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes executing. To force the events to be persisted immediately, you can call the ",(0,r.jsx)(t.code,{children:"register.flush()"})," method that is described in the next section."]}),"\n",(0,r.jsx)(t.h2,{id:"manually-flush-the-events",children:"Manually flush the events"}),"\n",(0,r.jsxs)(t.p,{children:["As mentioned in the previous section, events registered with ",(0,r.jsx)(t.code,{children:"register.events()"})," aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes its execution, but this doesn't work in all situations, sometimes it's useful to store partial updates of a longer process, and some scenarios could accept partial successes. To force the events to be persisted and wait for the database to confirm the write, you can use the ",(0,r.jsx)(t.code,{children:"register.flush()"})," method."]}),"\n",(0,r.jsxs)(t.p,{children:["The ",(0,r.jsx)(t.code,{children:"register.flush()"})," method takes no arguments and returns a promise that resolves when the events have been successfully persisted to the event store. For example:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public async handle(register: Register): Promise<void> {\n  // Do some work...\n  register.events(new OrderConfirmed(this.orderID))\n  await register.flush()\n  const mailID = await sendConfirmationEmail(this.orderID)\n  register.events(new MailSent(this.orderID, mailID))\n  // Do more work...\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["In this example, we're calling ",(0,r.jsx)(t.code,{children:"register.flush()"})," after registering an ",(0,r.jsx)(t.code,{children:"OrderConfirmed"})," event to ensure that it's persisted to the event store before continuing with the rest of the handler logic. In this way, even if an error happens while sending the confirmation email, the order will be persisted."]}),"\n",(0,r.jsx)(t.h2,{id:"access-the-current-signed-in-user",children:"Access the current signed in user"}),"\n",(0,r.jsxs)(t.p,{children:["When handling a command or event, you can use the injected ",(0,r.jsx)(t.code,{children:"Register"})," object to access the currently signed-in user as well as any metadata included in their JWT token like their roles or other claims (the specific claims will depend on the specific auth provider used). To do this, you can use the ",(0,r.jsx)(t.code,{children:"currentUser"})," property. This property is an instance of the ",(0,r.jsx)(t.code,{children:"UserEnvelope"})," class, which has the following properties:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"export interface UserEnvelope {\n  id?: string // An optional identifier of the user\n  username: string // The unique username of the current user\n  roles: Array<string> // The list of role names assigned to this user\n  claims: Record<string, unknown> // An object containing the claims included in the body of the JWT token\n  header?: Record<string, unknown> // An object containing the headers of the JWT token for further verification\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["For example, to access the username of the currently signed-in user, you can use the ",(0,r.jsx)(t.code,{children:"currentUser.username"})," property:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public async handle(register: Register): Promise<void> {\n  console.log(`The currently signed-in user is ${register.currentUser?.username}`)\n}\n\n// Output: The currently signed-in user is john.doe\n"})}),"\n",(0,r.jsx)(t.h2,{id:"command-specific-features",children:"Command-specific features"}),"\n",(0,r.jsx)(t.p,{children:"The command handlers are executed as part of a GraphQL mutation request, so they have access to a few additional features that are specific to commands that can be used to access the request context or alter the HTTP response headers."}),"\n",(0,r.jsx)(t.h3,{id:"access-the-request-context",children:"Access the request context"}),"\n",(0,r.jsxs)(t.p,{children:["The request context is injected in the command handler as part of the register command and you can access it using the ",(0,r.jsx)(t.code,{children:"context"})," property. This property is an instance of the ",(0,r.jsx)(t.code,{children:"ContextEnvelope"})," interface, which has the following properties:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"export interface ContextEnvelope {\n  /** Decoded request header and body */\n  request: {\n    headers: unknown\n    body: unknown\n  }\n  /** Provider-dependent raw request context object */\n  rawContext: unknown\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["The ",(0,r.jsx)(t.code,{children:"request"})," property exposes a normalized version of the request headers and body that can be used regardless the provider. We recommend using this property instead of the ",(0,r.jsx)(t.code,{children:"rawContext"})," property, as it will be more portable across providers."]}),"\n",(0,r.jsxs)(t.p,{children:["The ",(0,r.jsx)(t.code,{children:"rawContext"})," property exposes the full raw request context as it comes in the original request, so it will depend on the underlying provider used. For instance, in AWS, it will be ",(0,r.jsx)(t.a,{href:"https://docs.aws.amazon.com/lambda/latest/dg/nodejs-context.html",children:"a lambda context object"}),", while in Azure it will be ",(0,r.jsx)(t.a,{href:"https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-node#context-object",children:"an Azure Functions context object"}),"."]}),"\n",(0,r.jsx)(t.h3,{id:"alter-the-http-response-headers",children:"Alter the HTTP response headers"}),"\n",(0,r.jsxs)(t.p,{children:["Finally, you can use the ",(0,r.jsx)(t.code,{children:"responseHeaders"})," property to alter the HTTP response headers that will be sent back to the client. This property is a plain Typescript object which is initialized with the default headers. You can add, remove or modify any of the headers by using the standard object methods:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"public async handle(register: Register): Promise<void> {\n  register.responseHeaders['X-My-Header'] = 'My custom header'\n  register.responseHeaders['X-My-Other-Header'] = 'My other custom header'\n  delete register.responseHeaders['X-My-Other-Header']\n}\n"})})]})}function l(e={}){const{wrapper:t}={...(0,s.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(h,{...e})}):h(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>a,a:()=>o});var r=n(7294);const s={},i=r.createContext(s);function o(e){const t=r.useContext(i);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function a(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(s):e.components||s:o(e.components),r.createElement(i.Provider,{value:t},e.children)}}}]);