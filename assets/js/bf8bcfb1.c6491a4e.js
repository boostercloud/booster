"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4471],{5011:(e,n,o)=>{o.r(n),o.d(n,{assets:()=>d,contentTitle:()=>s,default:()=>c,frontMatter:()=>i,metadata:()=>a,toc:()=>l});var t=o(5893),r=o(1151);const i={},s="Logging in Booster",a={id:"features/logging",title:"Logging in Booster",description:"If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call different logging methods:",source:"@site/docs/03_features/04_logging.md",sourceDirName:"03_features",slug:"/features/logging",permalink:"/features/logging",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/04_logging.md",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1707997370,formattedLastUpdatedAt:"Feb 15, 2024",sidebarPosition:4,frontMatter:{},sidebar:"docs",previous:{title:"Schedule actions",permalink:"/features/schedule-actions"},next:{title:"Error handling",permalink:"/features/error-handling"}},d={},l=[{value:"Advanced logging",id:"advanced-logging",level:2},{value:"Using the Booster&#39;s logger",id:"using-the-boosters-logger",level:2}];function g(e){const n={admonition:"admonition",code:"code",em:"em",h1:"h1",h2:"h2",li:"li",p:"p",pre:"pre",ul:"ul",...(0,r.a)(),...e.components};return(0,t.jsxs)(t.Fragment,{children:[(0,t.jsx)(n.h1,{id:"logging-in-booster",children:"Logging in Booster"}),"\n",(0,t.jsx)(n.p,{children:"If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call different logging methods:"}),"\n",(0,t.jsxs)(n.ul,{children:["\n",(0,t.jsxs)(n.li,{children:[(0,t.jsx)(n.code,{children:"console.debug"})," for ",(0,t.jsx)(n.code,{children:"Level.debug"})]}),"\n",(0,t.jsxs)(n.li,{children:[(0,t.jsx)(n.code,{children:"console.info"})," for ",(0,t.jsx)(n.code,{children:"Level.info"})]}),"\n",(0,t.jsxs)(n.li,{children:[(0,t.jsx)(n.code,{children:"console.warn"})," for ",(0,t.jsx)(n.code,{children:"Level.warn"})]}),"\n",(0,t.jsxs)(n.li,{children:[(0,t.jsx)(n.code,{children:"console.error"})," for ",(0,t.jsx)(n.code,{children:"Level.error"})]}),"\n"]}),"\n",(0,t.jsx)(n.p,{children:"In this regard, there's no distinction from any other node process and you'll find the logs in your cloud provider's default log aggregator (i.e. Cloudwatch if you use AWS)."}),"\n",(0,t.jsx)(n.h2,{id:"advanced-logging",children:"Advanced logging"}),"\n",(0,t.jsxs)(n.p,{children:["You may need some advanced logging capabilities, such as redirecting your logs to a log aggregator. Booster also supports overriding the default behavior by providing custom loggers. The only thing you need to do is to provide an object that implements the ",(0,t.jsx)(n.code,{children:"Logger"})," interface at config time:"]}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-typescript",metastring:'title="@boostercloud/framework-types/lib/logger.ts"',children:"interface Logger {\n  debug(message?: any, ...optionalParams: any[]): void\n  info(message?: any, ...optionalParams: any[]): void\n  warn(message?: any, ...optionalParams: any[]): void\n  error(message?: any, ...optionalParams: any[]): void\n}\n"})}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-typescript",metastring:'title="src/config/config.ts"',children:"Booster.configure('development', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n  // highlight-start\n  config.logger = new MyCustomLogger() // Overrides the default logger object\n  config.logLevel = Level.debug        // Sets the log level at 'debug'     \n  config.logPrefix = 'my-store-dev'    // Sets the default prefix\n  // highlight-end\n})\n"})}),"\n",(0,t.jsx)(n.h2,{id:"using-the-boosters-logger",children:"Using the Booster's logger"}),"\n",(0,t.jsx)(n.p,{children:"All framework's components will use this logger by default and will generate logs that match the following pattern:"}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-text",children:"[<logPrefix>]|moduleName: <message>\n"})}),"\n",(0,t.jsxs)(n.p,{children:["You can get a custom logger instance that extends the configured logger by adding your moduleName and optionally overriding the configured prefix with the ",(0,t.jsx)(n.code,{children:"getLogger"})," helper function. It's a good practice to build and use a separate logger instance built with this method for each context, as this will make it easier to filter your logs when you need to investigate a problem."]}),"\n",(0,t.jsx)(n.p,{children:(0,t.jsx)(n.em,{children:"Example: Obtaining a logger for your command:"})}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-typescript",children:"@Command({\n  authorize: [User],\n})\nexport class UpdateShippingAddress {\n  public constructor(readonly cartId: UUID, readonly address: Address) {}\n\n  public static async handle(command: UpdateShippingAddress, register: Register): Promise<void> {\n    const logger = getLogger(Booster.config, 'UpdateShippingCommand#handler', 'MyApp')\n    logger.debug(`User ${register.currentUser?.username} changed shipping address for cart ${command.cartId}: ${JSON.stringify(command.address}`)\n    register.events(new ShippingAddressUpdated(command.cartId, command.address))\n  }\n}\n\n"})}),"\n",(0,t.jsxs)(n.p,{children:["When a ",(0,t.jsx)(n.code,{children:"UpdateShippingAddress"})," command is handled, it wil log messages that look like the following:"]}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-text",children:"[MyApp]|UpdateShippingCommand#handler: User buyer42 changed shipping address for cart 314: { street: '13th rue del percebe', number: 6, ... }\n"})}),"\n",(0,t.jsx)(n.admonition,{type:"info",children:(0,t.jsx)(n.p,{children:"Using the configured Booster logger is not mandatory for your application, but it might be convenient to centralize your logs and this is a standard way to do it."})})]})}function c(e={}){const{wrapper:n}={...(0,r.a)(),...e.components};return n?(0,t.jsx)(n,{...e,children:(0,t.jsx)(g,{...e})}):g(e)}},1151:(e,n,o)=>{o.d(n,{Z:()=>a,a:()=>s});var t=o(7294);const r={},i=t.createContext(r);function s(e){const n=t.useContext(i);return t.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function a(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(r):e.components||r:s(e.components),t.createElement(i.Provider,{value:n},e.children)}}}]);