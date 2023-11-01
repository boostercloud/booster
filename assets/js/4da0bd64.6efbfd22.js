"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6038],{3905:(e,t,n)=>{n.d(t,{Zo:()=>p,kt:()=>m});var r=n(7294);function o(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function a(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function i(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?a(Object(n),!0).forEach((function(t){o(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function c(e,t){if(null==e)return{};var n,r,o=function(e,t){if(null==e)return{};var n,r,o={},a=Object.keys(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||(o[n]=e[n]);return o}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var s=r.createContext({}),l=function(e){var t=r.useContext(s),n=t;return e&&(n="function"==typeof e?e(t):i(i({},t),e)),n},p=function(e){var t=l(e.components);return r.createElement(s.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},u=r.forwardRef((function(e,t){var n=e.components,o=e.mdxType,a=e.originalType,s=e.parentName,p=c(e,["components","mdxType","originalType","parentName"]),u=l(n),m=o,f=u["".concat(s,".").concat(m)]||u[m]||d[m]||a;return n?r.createElement(f,i(i({ref:t},p),{},{components:n})):r.createElement(f,i({ref:t},p))}));function m(e,t){var n=arguments,o=t&&t.mdxType;if("string"==typeof e||o){var a=n.length,i=new Array(a);i[0]=u;var c={};for(var s in t)hasOwnProperty.call(t,s)&&(c[s]=t[s]);c.originalType=e,c.mdxType="string"==typeof e?e:o,i[1]=c;for(var l=2;l<a;l++)i[l]=n[l];return r.createElement.apply(null,i)}return r.createElement.apply(null,n)}u.displayName="MDXCreateElement"},2066:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>s,contentTitle:()=>i,default:()=>d,frontMatter:()=>a,metadata:()=>c,toc:()=>l});var r=n(7462),o=(n(7294),n(3905));const a={},i="Booster instrumentation",c={unversionedId:"going-deeper/instrumentation",id:"going-deeper/instrumentation",title:"Booster instrumentation",description:"Trace Decorator",source:"@site/docs/10_going-deeper/instrumentation.md",sourceDirName:"10_going-deeper",slug:"/going-deeper/instrumentation",permalink:"/going-deeper/instrumentation",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/instrumentation.md",tags:[],version:"current",lastUpdatedBy:"Javier Toledo",lastUpdatedAt:1698840899,formattedLastUpdatedAt:"Nov 1, 2023",frontMatter:{},sidebar:"docs",previous:{title:"Framework packages",permalink:"/going-deeper/framework-packages"},next:{title:"Frequently Asked Questions",permalink:"/frequently-asked-questions"}},s={},l=[{value:"Trace Decorator",id:"trace-decorator",level:2},{value:"Usage",id:"usage",level:3},{value:"TraceActionTypes",id:"traceactiontypes",level:3},{value:"TraceInfo",id:"traceinfo",level:3},{value:"Adding the Trace Decorator to Your own async methods",id:"adding-the-trace-decorator-to-your-own-async-methods",level:3}],p={toc:l};function d(e){let{components:t,...n}=e;return(0,o.kt)("wrapper",(0,r.Z)({},p,n,{components:t,mdxType:"MDXLayout"}),(0,o.kt)("h1",{id:"booster-instrumentation"},"Booster instrumentation"),(0,o.kt)("h2",{id:"trace-decorator"},"Trace Decorator"),(0,o.kt)("p",null,"The Trace Decorator is a ",(0,o.kt)("strong",{parentName:"p"},"Booster")," functionality that facilitates the reception of notifications whenever significant events occur in Booster's core, such as event dispatching or migration execution."),(0,o.kt)("h3",{id:"usage"},"Usage"),(0,o.kt)("p",null,"To configure a custom tracer, you need to define an object with two methods: onStart and onEnd. The onStart method is called before the traced method is invoked, and the onEnd method is called after the method completes. Both methods receive a TraceInfo object, which contains information about the traced method and its arguments."),(0,o.kt)("p",null,"Here's an example of a custom tracer that logs trace events to the console:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"import {\n  TraceParameters,\n  BoosterConfig,\n  TraceActionTypes,\n} from '@boostercloud/framework-types'\n\nclass MyTracer {\n  static async onStart(config: BoosterConfig, actionType: string, traceParameters: TraceParameters): Promise<void> {\n    console.log(`Start ${actionType}: ${traceParameters.className}.${traceParameters.methodName}`)\n  }\n\n  static async onEnd(config: BoosterConfig, actionType: string, traceParameters: TraceParameters): Promise<void> {\n    console.log(`End ${actionType}: ${traceParameters.className}.${traceParameters.methodName}`)\n  }\n}\n")),(0,o.kt)("p",null,"You can then configure the tracer in your Booster application's configuration:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"import { BoosterConfig } from '@boostercloud/framework-types'\nimport { MyTracer } from './my-tracer'\n\nconst config: BoosterConfig = {\n// ...other configuration options...\n  trace: {\n    enableTraceNotification: true,\n    onStart: MyTracer.onStart,\n    onEnd: MyTracer.onStart,\n  }\n}\n")),(0,o.kt)("p",null,"In the configuration above, we've enabled trace notifications and specified our onStart and onEnd as the methods to use. Verbose disable will reduce the amount of information generated excluding the internal parameter in the trace parameters. "),(0,o.kt)("p",null,"Setting ",(0,o.kt)("inlineCode",{parentName:"p"},"enableTraceNotification: true")," would enable the trace for all actions. You can either disable them by setting it to ",(0,o.kt)("inlineCode",{parentName:"p"},"false")," or selectively enable only specific actions using an array of TraceActionTypes."),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"import { BoosterConfig, TraceActionTypes } from '@boostercloud/framework-types'\nimport { MyTracer } from './my-tracer'\n\nconst config: BoosterConfig = {\n// ...other configuration options...\n  trace: {\n    enableTraceNotification: [TraceActionTypes.DISPATCH_EVENT, TraceActionTypes.MIGRATION_RUN, 'OTHER'],\n    includeInternal: false,\n    onStart: MyTracer.onStart,\n    onEnd: MyTracer.onStart,\n  }\n}\n")),(0,o.kt)("p",null,"In this example, only DISPATCH_EVENT, MIGRATION_RUN and 'OTHER' actions will trigger trace notifications."),(0,o.kt)("h3",{id:"traceactiontypes"},"TraceActionTypes"),(0,o.kt)("p",null,"The TraceActionTypes enum defines all the traceable actions in Booster's core:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"export enum TraceActionTypes {\n  CUSTOM,\n  EVENT_HANDLERS_PROCESS,\n  HANDLE_EVENT,\n  DISPATCH_ENTITY_TO_EVENT_HANDLERS,\n  DISPATCH_EVENTS,\n  FETCH_ENTITY_SNAPSHOT,\n  STORE_SNAPSHOT,\n  LOAD_LATEST_SNAPSHOT,\n  LOAD_EVENT_STREAM_SINCE,\n  ENTITY_REDUCER,\n  READ_MODEL_FIND_BY_ID,\n  GRAPHQL_READ_MODEL_SEARCH,\n  READ_MODEL_SEARCH,\n  COMMAND_HANDLER,\n  MIGRATION_RUN,\n  GRAPHQL_DISPATCH,\n  GRAPHQL_RUN_OPERATION,\n  SCHEDULED_COMMAND_HANDLER,\n  DISPATCH_SUBSCRIBER_NOTIFIER,\n  READ_MODEL_SCHEMA_MIGRATOR_RUN,\n  SCHEMA_MIGRATOR_MIGRATE,\n}\n")),(0,o.kt)("h3",{id:"traceinfo"},"TraceInfo"),(0,o.kt)("p",null,"The TraceInfo interface defines the data that is passed to the tracer's onBefore and onAfter methods:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"export interface TraceInfo {\n  className: string\n  methodName: string\n  args: Array<unknown>\n  traceId: UUID\n  elapsedInvocationMillis?: number\n  internal: {\n    target: unknown\n    descriptor: PropertyDescriptor\n  }\n  description?: string\n}\n")),(0,o.kt)("p",null,(0,o.kt)("inlineCode",{parentName:"p"},"className")," and ",(0,o.kt)("inlineCode",{parentName:"p"},"methodName")," identify the function that is being traced."),(0,o.kt)("h3",{id:"adding-the-trace-decorator-to-your-own-async-methods"},"Adding the Trace Decorator to Your own async methods"),(0,o.kt)("p",null,"In addition to using the Trace Decorator to receive notifications when events occur in Booster's core, you can also use it to trace your own methods. To add the Trace Decorator to your own methods, simply add @Trace() before your method declaration."),(0,o.kt)("p",null,"Here's an example of how to use the Trace Decorator on a custom method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"import { Trace } from '@boostercloud/framework-core'\nimport { BoosterConfig, Logger } from '@boostercloud/framework-types'\n\nexport class MyCustomClass {\n  @Trace('OTHER')\n  public async myCustomMethod(config: BoosterConfig, logger: Logger): Promise<void> {\n    logger.debug('This is my custom method')\n    // Do some custom logic here...\n  }\n}\n")),(0,o.kt)("p",null,"In the example above, we added the @Trace('OTHER') decorator to the myCustomMethod method. This will cause the method to emit trace events when it's invoked, allowing you to trace the flow of your application and detect performance bottlenecks or errors."),(0,o.kt)("p",null,"Note that when you add the Trace Decorator to your own methods, you'll need to configure your Booster instance to use a tracer that implements the necessary methods to handle these events."))}d.isMDXComponent=!0}}]);