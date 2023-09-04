"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6097],{3905:(e,r,n)=>{n.d(r,{Zo:()=>h,kt:()=>u});var t=n(7294);function o(e,r,n){return r in e?Object.defineProperty(e,r,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[r]=n,e}function a(e,r){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var t=Object.getOwnPropertySymbols(e);r&&(t=t.filter((function(r){return Object.getOwnPropertyDescriptor(e,r).enumerable}))),n.push.apply(n,t)}return n}function l(e){for(var r=1;r<arguments.length;r++){var n=null!=arguments[r]?arguments[r]:{};r%2?a(Object(n),!0).forEach((function(r){o(e,r,n[r])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(r){Object.defineProperty(e,r,Object.getOwnPropertyDescriptor(n,r))}))}return e}function i(e,r){if(null==e)return{};var n,t,o=function(e,r){if(null==e)return{};var n,t,o={},a=Object.keys(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||(o[n]=e[n]);return o}(e,r);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var d=t.createContext({}),s=function(e){var r=t.useContext(d),n=r;return e&&(n="function"==typeof e?e(r):l(l({},r),e)),n},h=function(e){var r=s(e.components);return t.createElement(d.Provider,{value:r},e.children)},c={inlineCode:"code",wrapper:function(e){var r=e.children;return t.createElement(t.Fragment,{},r)}},p=t.forwardRef((function(e,r){var n=e.components,o=e.mdxType,a=e.originalType,d=e.parentName,h=i(e,["components","mdxType","originalType","parentName"]),p=s(n),u=o,m=p["".concat(d,".").concat(u)]||p[u]||c[u]||a;return n?t.createElement(m,l(l({ref:r},h),{},{components:n})):t.createElement(m,l({ref:r},h))}));function u(e,r){var n=arguments,o=r&&r.mdxType;if("string"==typeof e||o){var a=n.length,l=new Array(a);l[0]=p;var i={};for(var d in r)hasOwnProperty.call(r,d)&&(i[d]=r[d]);i.originalType=e,i.mdxType="string"==typeof e?e:o,l[1]=i;for(var s=2;s<a;s++)l[s]=n[s];return t.createElement.apply(null,l)}return t.createElement.apply(null,n)}p.displayName="MDXCreateElement"},9444:(e,r,n)=>{n.r(r),n.d(r,{assets:()=>d,contentTitle:()=>l,default:()=>c,frontMatter:()=>a,metadata:()=>i,toc:()=>s});var t=n(7462),o=(n(7294),n(3905));const a={},l="Error handling",i={unversionedId:"features/error-handling",id:"features/error-handling",title:"Error handling",description:"Error handling in Booster",source:"@site/docs/03_features/05_error-handling.md",sourceDirName:"03_features",slug:"/features/error-handling",permalink:"/features/error-handling",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/05_error-handling.md",tags:[],version:"current",lastUpdatedBy:"Javier Toledo",lastUpdatedAt:1693839209,formattedLastUpdatedAt:"Sep 4, 2023",sidebarPosition:5,frontMatter:{},sidebar:"docs",previous:{title:"Logging in Booster",permalink:"/features/logging"},next:{title:"Security",permalink:"/security/security"}},d={},s=[{value:"Error handling in Booster",id:"error-handling-in-booster",level:2},{value:"Custom error handling",id:"custom-error-handling",level:3},{value:"Command handle errors",id:"command-handle-errors",level:3},{value:"Scheduled command handle errors",id:"scheduled-command-handle-errors",level:3},{value:"Event handler errors",id:"event-handler-errors",level:3},{value:"Reducer errors",id:"reducer-errors",level:3},{value:"Projection errors",id:"projection-errors",level:3},{value:"All errors",id:"all-errors",level:3},{value:"Global error handler example",id:"global-error-handler-example",level:2}],h={toc:s};function c(e){let{components:r,...n}=e;return(0,o.kt)("wrapper",(0,t.Z)({},h,n,{components:r,mdxType:"MDXLayout"}),(0,o.kt)("h1",{id:"error-handling"},"Error handling"),(0,o.kt)("h2",{id:"error-handling-in-booster"},"Error handling in Booster"),(0,o.kt)("p",null,"Booster provides a default error handling mechanism that will try to catch all the errors that are thrown in the application and will log them. This is useful for debugging purposes, but you may want to customize the error handling in your application. For example, you may want to send an email to the administrator when an error occurs."),(0,o.kt)("h3",{id:"custom-error-handling"},"Custom error handling"),(0,o.kt)("p",null,"To customize the error handling, you need to create a class decorated with the ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," decorator. This class will contain the methods that will be called when an error is thrown. There is one method for each component in the application where an error can be thrown. All these functions receive the error that was thrown and the information about the component that was being executed when the error occurred. "),(0,o.kt)("p",null,"They must return a promise that resolves to an ",(0,o.kt)("inlineCode",{parentName:"p"},"Error")," object or ",(0,o.kt)("inlineCode",{parentName:"p"},"undefined"),". If the promise resolves to ",(0,o.kt)("inlineCode",{parentName:"p"},"undefined"),", the error will be ignored and the application will continue working. If the promise resolves to an ",(0,o.kt)("inlineCode",{parentName:"p"},"Error")," object, the error will be thrown."),(0,o.kt)("h3",{id:"command-handle-errors"},"Command handle errors"),(0,o.kt)("p",null,"These are the errors that are thrown in the ",(0,o.kt)("inlineCode",{parentName:"p"},"handle")," method of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Command"),". You can catch and return new errors in this method annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"Tis method receives the error that was thrown and the command that was being handled when the error occurred. "),(0,o.kt)("h3",{id:"scheduled-command-handle-errors"},"Scheduled command handle errors"),(0,o.kt)("p",null,"These are the errors that are thrown in the ",(0,o.kt)("inlineCode",{parentName:"p"},"handle")," method of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@ScheduledCommand"),". You can catch and return new errors in this function annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"This method receives the error that was thrown."),(0,o.kt)("h3",{id:"event-handler-errors"},"Event handler errors"),(0,o.kt)("p",null,"These are the errors that are thrown in the ",(0,o.kt)("inlineCode",{parentName:"p"},"handle")," method of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Event"),". You can catch and return new errors in this function annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onEventHandlerError(error: Error, event: EventEnvelope): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"This method receives the error that was thrown and the event that was being handled when the error occurred."),(0,o.kt)("h3",{id:"reducer-errors"},"Reducer errors"),(0,o.kt)("p",null,"These are the errors that are thrown in the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Reduces")," method of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Entity"),". You can catch and return new errors in this function annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onReducerError(error: Error, entity: EntityInterface, snapshot: EntityInterface | null): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"This method receives the error that was thrown, the name of the entity, the ID of the entity, and the name of the reducer."),(0,o.kt)("h3",{id:"projection-errors"},"Projection errors"),(0,o.kt)("p",null,"These are the errors that are thrown in the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Projects")," method of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@ReadModel"),". You can catch and return new errors in this function annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onProjectionError(error: Error, readModel: ReadModelInterface, entity: EntityInterface): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"This method receives the error that was thrown, the name of the read model, the ID of the read model, and the name of the projection."),(0,o.kt)("h3",{id:"all-errors"},"All errors"),(0,o.kt)("p",null,"These are the errors that are thrown in any of the previous methods. You can catch and return new errors in this function annotating a class with ",(0,o.kt)("inlineCode",{parentName:"p"},"@GlobalErrorHandler")," and implementing the following method:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class MyErrorHandler {\n  public onError(error: Error | undefined): Promise<Error | undefined> {\n    // Do something with the error\n  }\n}\n")),(0,o.kt)("p",null,"This method receives the error that was thrown."),(0,o.kt)("h2",{id:"global-error-handler-example"},"Global error handler example"),(0,o.kt)("p",null,"You can implement all error handling functions in the same class. Here is an example of a global error handler that will handle all the errors mentioned above:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@GlobalErrorHandler()\nexport class AppErrorHandler {\n  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onReducerError(\n    error: Error,\n    eventInstance: EventInterface,\n    snapshotInstance: EntityInterface | null\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onProjectionError(\n    error: Error,\n    entity: EntityInterface,\n    readModel: ReadModelInterface | undefined\n  ): Promise<Error | undefined> {\n    return error\n  }\n\n  public static async onError(error: Error | undefined): Promise<Error | undefined> {\n    return error\n  }\n}\n")))}c.isMDXComponent=!0}}]);