"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4471],{3905:(e,t,n)=>{n.d(t,{Zo:()=>d,kt:()=>u});var r=n(7294);function o(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function a(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function i(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?a(Object(n),!0).forEach((function(t){o(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,r,o=function(e,t){if(null==e)return{};var n,r,o={},a=Object.keys(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||(o[n]=e[n]);return o}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var s=r.createContext({}),g=function(e){var t=r.useContext(s),n=t;return e&&(n="function"==typeof e?e(t):i(i({},t),e)),n},d=function(e){var t=g(e.components);return r.createElement(s.Provider,{value:t},e.children)},p={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},c=r.forwardRef((function(e,t){var n=e.components,o=e.mdxType,a=e.originalType,s=e.parentName,d=l(e,["components","mdxType","originalType","parentName"]),c=g(n),u=o,m=c["".concat(s,".").concat(u)]||c[u]||p[u]||a;return n?r.createElement(m,i(i({ref:t},d),{},{components:n})):r.createElement(m,i({ref:t},d))}));function u(e,t){var n=arguments,o=t&&t.mdxType;if("string"==typeof e||o){var a=n.length,i=new Array(a);i[0]=c;var l={};for(var s in t)hasOwnProperty.call(t,s)&&(l[s]=t[s]);l.originalType=e,l.mdxType="string"==typeof e?e:o,i[1]=l;for(var g=2;g<a;g++)i[g]=n[g];return r.createElement.apply(null,i)}return r.createElement.apply(null,n)}c.displayName="MDXCreateElement"},9860:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>s,contentTitle:()=>i,default:()=>p,frontMatter:()=>a,metadata:()=>l,toc:()=>g});var r=n(7462),o=(n(7294),n(3905));const a={},i="Logging in Booster",l={unversionedId:"features/logging",id:"features/logging",title:"Logging in Booster",description:"If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call different logging methods:",source:"@site/docs/03_features/04_logging.md",sourceDirName:"03_features",slug:"/features/logging",permalink:"/features/logging",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/04_logging.md",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1700228463,formattedLastUpdatedAt:"Nov 17, 2023",sidebarPosition:4,frontMatter:{},sidebar:"docs",previous:{title:"Schedule actions",permalink:"/features/schedule-actions"},next:{title:"Error handling",permalink:"/features/error-handling"}},s={},g=[{value:"Advanced logging",id:"advanced-logging",level:2},{value:"Using the Booster&#39;s logger",id:"using-the-boosters-logger",level:2}],d={toc:g};function p(e){let{components:t,...n}=e;return(0,o.kt)("wrapper",(0,r.Z)({},d,n,{components:t,mdxType:"MDXLayout"}),(0,o.kt)("h1",{id:"logging-in-booster"},"Logging in Booster"),(0,o.kt)("p",null,"If no configuration is provided, Booster uses the default JavaScript logging capabilities. Depending on the log level, it will call different logging methods:"),(0,o.kt)("ul",null,(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"console.debug")," for ",(0,o.kt)("inlineCode",{parentName:"li"},"Level.debug")),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"console.info")," for ",(0,o.kt)("inlineCode",{parentName:"li"},"Level.info")),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"console.warn")," for ",(0,o.kt)("inlineCode",{parentName:"li"},"Level.warn")),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"console.error")," for ",(0,o.kt)("inlineCode",{parentName:"li"},"Level.error"))),(0,o.kt)("p",null,"In this regard, there's no distinction from any other node process and you'll find the logs in your cloud provider's default log aggregator (i.e. Cloudwatch if you use AWS)."),(0,o.kt)("h2",{id:"advanced-logging"},"Advanced logging"),(0,o.kt)("p",null,"You may need some advanced logging capabilities, such as redirecting your logs to a log aggregator. Booster also supports overriding the default behavior by providing custom loggers. The only thing you need to do is to provide an object that implements the ",(0,o.kt)("inlineCode",{parentName:"p"},"Logger")," interface at config time:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript",metastring:'title="@boostercloud/framework-types/lib/logger.ts"',title:'"@boostercloud/framework-types/lib/logger.ts"'},"interface Logger {\n  debug(message?: any, ...optionalParams: any[]): void\n  info(message?: any, ...optionalParams: any[]): void\n  warn(message?: any, ...optionalParams: any[]): void\n  error(message?: any, ...optionalParams: any[]): void\n}\n")),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript",metastring:'title="src/config/config.ts"',title:'"src/config/config.ts"'},"Booster.configure('development', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n  // highlight-start\n  config.logger = new MyCustomLogger() // Overrides the default logger object\n  config.logLevel = Level.debug        // Sets the log level at 'debug'     \n  config.logPrefix = 'my-store-dev'    // Sets the default prefix\n  // highlight-end\n})\n")),(0,o.kt)("h2",{id:"using-the-boosters-logger"},"Using the Booster's logger"),(0,o.kt)("p",null,"All framework's components will use this logger by default and will generate logs that match the following pattern:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"[<logPrefix>]|moduleName: <message>\n")),(0,o.kt)("p",null,"You can get a custom logger instance that extends the configured logger by adding your moduleName and optionally overriding the configured prefix with the ",(0,o.kt)("inlineCode",{parentName:"p"},"getLogger")," helper function. It's a good practice to build and use a separate logger instance built with this method for each context, as this will make it easier to filter your logs when you need to investigate a problem."),(0,o.kt)("p",null,(0,o.kt)("em",{parentName:"p"},"Example: Obtaining a logger for your command:")),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@Command({\n  authorize: [User],\n})\nexport class UpdateShippingAddress {\n  public constructor(readonly cartId: UUID, readonly address: Address) {}\n\n  public static async handle(command: UpdateShippingAddress, register: Register): Promise<void> {\n    const logger = getLogger(Booster.config, 'UpdateShippingCommand#handler', 'MyApp')\n    logger.debug(`User ${register.currentUser?.username} changed shipping address for cart ${command.cartId}: ${JSON.stringify(command.address}`)\n    register.events(new ShippingAddressUpdated(command.cartId, command.address))\n  }\n}\n\n")),(0,o.kt)("p",null,"When a ",(0,o.kt)("inlineCode",{parentName:"p"},"UpdateShippingAddress")," command is handled, it wil log messages that look like the following:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"[MyApp]|UpdateShippingCommand#handler: User buyer42 changed shipping address for cart 314: { street: '13th rue del percebe', number: 6, ... }\n")),(0,o.kt)("admonition",{type:"info"},(0,o.kt)("p",{parentName:"admonition"},"Using the configured Booster logger is not mandatory for your application, but it might be convenient to centralize your logs and this is a standard way to do it.")))}p.isMDXComponent=!0}}]);