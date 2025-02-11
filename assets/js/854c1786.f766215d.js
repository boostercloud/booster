"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6097],{5189:(e,r,n)=>{n.r(r),n.d(r,{assets:()=>i,contentTitle:()=>d,default:()=>h,frontMatter:()=>a,metadata:()=>l,toc:()=>s});var o=n(5893),t=n(1151);const a={},d="Error handling",l={id:"features/error-handling",title:"Error handling",description:"Error handling in Booster",source:"@site/docs/03_features/05_error-handling.md",sourceDirName:"03_features",slug:"/features/error-handling",permalink:"/features/error-handling",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/05_error-handling.md",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1739295071,formattedLastUpdatedAt:"Feb 11, 2025",sidebarPosition:5,frontMatter:{},sidebar:"docs",previous:{title:"Logging in Booster",permalink:"/features/logging"},next:{title:"Security",permalink:"/security/security"}},i={},s=[{value:"Error handling in Booster",id:"error-handling-in-booster",level:2},{value:"Custom error handling",id:"custom-error-handling",level:3},{value:"Command handle errors",id:"command-handle-errors",level:3},{value:"Scheduled command handle errors",id:"scheduled-command-handle-errors",level:3},{value:"Event handler errors",id:"event-handler-errors",level:3},{value:"Reducer errors",id:"reducer-errors",level:3},{value:"Event errors",id:"event-errors",level:3},{value:"Projection errors",id:"projection-errors",level:3},{value:"All errors",id:"all-errors",level:3},{value:"Global error handler example",id:"global-error-handler-example",level:2}];function c(e){const r={code:"code",h1:"h1",h2:"h2",h3:"h3",p:"p",pre:"pre",strong:"strong",...(0,t.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(r.h1,{id:"error-handling",children:"Error handling"}),"\n",(0,o.jsx)(r.h2,{id:"error-handling-in-booster",children:"Error handling in Booster"}),"\n",(0,o.jsx)(r.p,{children:"Booster provides a default error handling mechanism that will try to catch all the errors that are thrown in the application and will log them. This is useful for debugging purposes, but you may want to customize the error handling in your application. For example, you may want to email the administrator when an error occurs."}),"\n",(0,o.jsx)(r.h3,{id:"custom-error-handling",children:"Custom error handling"}),"\n",(0,o.jsxs)(r.p,{children:["To customize the error handling, you need to create a class decorated with the ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," decorator. This class will contain the methods that will be called when an error is thrown. There is one method for each component in the application where an error can be thrown. All these functions receive the error that was thrown and the information about the component that was being executed when the error occurred."]}),"\n",(0,o.jsxs)(r.p,{children:["They must return a promise that resolves to an ",(0,o.jsx)(r.code,{children:"Error"})," object or ",(0,o.jsx)(r.code,{children:"undefined"}),". If the promise resolves to ",(0,o.jsx)(r.code,{children:"undefined"}),", the error will be ignored and ",(0,o.jsx)(r.strong,{children:"Booster"})," will continue working. If the promise resolves to an ",(0,o.jsx)(r.code,{children:"Error"})," object, the error will be thrown and ",(0,o.jsx)(r.strong,{children:"Booster"})," will handle it on a case-by-case basis in the default way."]}),"\n",(0,o.jsx)(r.h3,{id:"command-handle-errors",children:"Command handle errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@Command"}),". You can catch and return new errors in this method annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onCommandHandlerError(\n    error: Error,\n    commandEnvelope: CommandEnvelope,\n    commandMetadata: CommandMetadata\n  ): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown and the command that was being handled when the error occurred."}),"\n",(0,o.jsx)(r.h3,{id:"scheduled-command-handle-errors",children:"Scheduled command handle errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@ScheduledCommand"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onScheduledCommandHandlerError(\n    error: Error,\n    scheduledCommandEnvelope: ScheduledCommandEnvelope,\n    scheduledCommandMetadata: ScheduledCommandMetadata\n  ): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsxs)(r.p,{children:["Note that if an error is thrown on a ScheduleCommand, ",(0,o.jsx)(r.strong,{children:"Booster"})," will stop working."]}),"\n",(0,o.jsx)(r.h3,{id:"event-handler-errors",children:"Event handler errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"handle"})," method of the ",(0,o.jsx)(r.code,{children:"@Event"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onDispatchEventHandlerError(\n    error: Error,\n    eventEnvelope: EventEnvelope | NotificationInterface,\n    eventHandlerMetadata: unknown,\n    eventInstance: EventInterface\n  ): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.h3,{id:"reducer-errors",children:"Reducer errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"@Reduces"})," method of the ",(0,o.jsx)(r.code,{children:"@Entity"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onReducerError(\n    error: Error,\n    eventEnvelope: EventEnvelope,\n    reducerMetadata: ReducerMetadata,\n    eventInstance: EventInterface,\n    snapshotInstance: EntityInterface | null\n  ): Promise<Error> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"Note you can not ignore a Reducer error as the new entity could not be created"}),"\n",(0,o.jsx)(r.h3,{id:"event-errors",children:"Event errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown if the event doesn't exist. You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onEventError(error: Error, eventEnvelope: EventEnvelope): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown and the event received."}),"\n",(0,o.jsx)(r.h3,{id:"projection-errors",children:"Projection errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in the ",(0,o.jsx)(r.code,{children:"@Projects"})," method of the ",(0,o.jsx)(r.code,{children:"@ReadModel"}),". You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public static async onProjectionError(\n    error: Error,\n    entityEnvelope: EntitySnapshotEnvelope,\n    projectionMetadata: ProjectionMetadata<EntityInterface>,\n    entity: EntityInterface,\n    readModel: ReadModelInterface | undefined\n  ): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.h3,{id:"all-errors",children:"All errors"}),"\n",(0,o.jsxs)(r.p,{children:["These are the errors that are thrown in any of the previous methods. You can catch and return new errors in this function annotating a class with ",(0,o.jsx)(r.code,{children:"@GlobalErrorHandler"})," and implementing the following method:"]}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onError(error: Error | undefined): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n"})}),"\n",(0,o.jsx)(r.p,{children:"This method receives the error that was thrown."}),"\n",(0,o.jsx)(r.h2,{id:"global-error-handler-example",children:"Global error handler example"}),"\n",(0,o.jsx)(r.p,{children:"You can implement all error handling functions in the same class. Here is an example of a global error handler that will handle all the errors mentioned above:"}),"\n",(0,o.jsx)(r.pre,{children:(0,o.jsx)(r.code,{className:"language-typescript",children:"@GlobalErrorHandler()\nexport class AppErrorHandler {\n  public static async onCommandHandlerError(\n    error: Error,\n    commandEnvelope: CommandEnvelope,\n    commandMetadata: CommandMetadata\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onScheduledCommandHandlerError(\n    error: Error,\n    scheduledCommandEnvelope: ScheduledCommandEnvelope,\n    scheduledCommandMetadata: ScheduledCommandMetadata\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onDispatchEventHandlerError(\n    error: Error,\n    eventEnvelope: EventEnvelope | NotificationInterface,\n    eventHandlerMetadata: unknown,\n    eventInstance: EventInterface\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onReducerError(\n    error: Error,\n    eventEnvelope: EventEnvelope,\n    reducerMetadata: ReducerMetadata,\n    eventInstance: EventInterface,\n    snapshotInstance: EntityInterface | null\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onProjectionError(\n    error: Error,\n    entityEnvelope: EntitySnapshotEnvelope,\n    projectionMetadata: ProjectionMetadata<EntityInterface>,\n    entity: EntityInterface,\n    readModel: ReadModelInterface | undefined\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onEventError(error: Error, eventEnvelope: EventEnvelope): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onError(error: Error | undefined): Promise<Error | undefined> {\n    return error\n  }\n}\n"})})]})}function h(e={}){const{wrapper:r}={...(0,t.a)(),...e.components};return r?(0,o.jsx)(r,{...e,children:(0,o.jsx)(c,{...e})}):c(e)}},1151:(e,r,n)=>{n.d(r,{Z:()=>l,a:()=>d});var o=n(7294);const t={},a=o.createContext(t);function d(e){const r=o.useContext(a);return o.useMemo((function(){return"function"==typeof e?e(r):{...r,...e}}),[r,e])}function l(e){let r;return r=e.disableParentContext?"function"==typeof e.components?e.components(t):e.components||t:d(e.components),o.createElement(a.Provider,{value:r},e.children)}}}]);