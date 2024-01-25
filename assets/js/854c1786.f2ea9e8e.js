"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6097],{5189:(e,r,n)=>{n.r(r),n.d(r,{assets:()=>i,contentTitle:()=>d,default:()=>c,frontMatter:()=>a,metadata:()=>l,toc:()=>s});var o=n(5893),t=n(1151);const a={},d="Error handling",l={id:"features/error-handling",title:"Error handling",description:"Error handling in Booster",source:"@site/docs/03_features/05_error-handling.md",sourceDirName:"03_features",slug:"/features/error-handling",permalink:"/features/error-handling",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/05_error-handling.md",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1706192347,formattedLastUpdatedAt:"Jan 25, 2024",sidebarPosition:5,frontMatter:{},sidebar:"docs",previous:{title:"Logging in Booster",permalink:"/features/logging"},next:{title:"Security",permalink:"/security/security"}},i={},s=[{value:"Error handling in Booster",id:"error-handling-in-booster",level:2},{value:"Custom error handling",id:"custom-error-handling",level:3},{value:"Command handle errors",id:"command-handle-errors",level:3},{value:"Scheduled command handle errors",id:"scheduled-command-handle-errors",level:3},{value:"Event handler errors",id:"event-handler-errors",level:3},{value:"Reducer errors",id:"reducer-errors",level:3},{value:"Projection errors",id:"projection-errors",level:3},{value:"All errors",id:"all-errors",level:3},{value:"Global error handler example",id:"global-error-handler-example",level:2}];function h(e){const r={code:"code",h1:"h1",h2:"h2",h3:"h3",p:"p",pre:"pre",...(0,t.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(r.h1,{id:"error-handling",children:"Error handling"}),"\n",(0,o.jsx)(r.h2,{id:"error-handling-in-booster",children:"Error handling in Booster"}),"\n",(0,o.jsx)(r.p,{children:"Booster provides a default error handling mechanism that will try to catch all the errors that are thrown in the application and will log them. This is useful for debugging purposes, but you may want to customize the error handling in your application. For example, you may want to send an email to the administrator when an error occurs."}),"\n",(0,o.jsx)(r.h3,{id:"custom-error-handling",children:"Custom error handling"}),"\n",(0,o.jsxs)(r.p,{children:["To customize the error handling, you need to create a class decorated with the ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," decorator. This class will contain the methods that will be called when an error is thrown. There is one method for each component in the application where an error can be thrown. All these functions receive the error that was thrown and the information about the component that was being executed when the error occurred."]}),"\n",(0,o.jsxs)(r.p,{children:["They must return a promise that resolves to an ",(0,o.jsx)(r.code,{children:"Error"})," object or ",(0,o.jsx)(r.code,{children:"undefined"}),". If the promise resolves to ",(0,o.jsx)(r.code,{children:"undefined"}),", the error will be ignored and the application will continue working. If the promise resolves to an ",(0,o.jsx)(r.code,{children:"Error"})," object, the error will be thrown."]}),"\n",(0,o.jsx)(r.h3,{id:"command-handle-errors",children:"Command handle errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@Command"}),". You can catch and return new errors in this method annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"Tis method receives the error that was thrown and the command that was being handled when the error occurred."}),"\n",(0,o.jsx)(r.h3,{id:"scheduled-command-handle-errors",children:"Scheduled command handle errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@ScheduledCommand"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown."}),"\n",(0,o.jsx)(r.h3,{id:"event-handler-errors",children:"Event handler errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@Event"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onEventHandlerError(error: Error, event: EventEnvelope): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown and the event that was being handled when the error occurred."}),"\n",(0,o.jsx)(r.h3,{id:"reducer-errors",children:"Reducer errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"@Reduces"})," method of the ",(0,o.jsx)(r.code,{children:"@Entity"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onReducerError(error: Error, entity: EntityInterface, snapshot: EntityInterface | null): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown, the name of the entity, the ID of the entity, and the name of the reducer."}),"\n",(0,o.jsx)(r.h3,{id:"projection-errors",children:"Projection errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"@Projects"})," method of the ",(0,o.jsx)(r.code,{children:"@ReadModel"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onProjectionError(error: Error, readModel: ReadModelInterface, entity: EntityInterface): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown, the name of the read model, the ID of the read model, and the name of the projection."}),"\n",(0,o.jsx)(r.h3,{id:"all-errors",children:"All errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in any of the previous methods. You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onError(error: Error | undefined): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown."}),"\n",(0,o.jsx)(r.h2,{id:"global-error-handler-example",children:"Global error handler example"}),"\n",(0,o.jsx)(r.p,{children:"You can implement all error handling functions in the same class. Here is an example of a global error handler that will handle all the errors mentioned above:"}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class AppErrorHandler {\n  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onReducerError(\n    error: Error,\n    eventInstance: EventInterface,\n    snapshotInstance: EntityInterface | null\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onProjectionError(\n    error: Error,\n    entity: EntityInterface,\n    readModel: ReadModelInterface | undefined\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onError(error: Error | undefined): Promise<Error | undefined> {\n    return error\n  }\n}\n"})})]})}function c(e={}){const{wrapper:r}={...(0,t.a)(),...e.components};return r?(0,o.jsx)(r,{...e,children:(0,o.jsx)(h,{...e})}):h(e)}},1151:(e,r,n)=>{n.d(r,{Z:()=>l,a:()=>d});var o=n(7294);const t={},a=o.createContext(t);function d(e){const r=o.useContext(a);return o.useMemo((function(){return"function"==typeof e?e(r):{...r,...e}}),[r,e])}function l(e){let r;return r=e.disableParentContext?"function"==typeof e.components?e.components(t):e.components||t:d(e.components),o.createElement(a.Provider,{value:r},e.children)}}}]);