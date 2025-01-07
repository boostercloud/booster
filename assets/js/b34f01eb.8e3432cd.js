"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[8297],{6185:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>a,contentTitle:()=>r,default:()=>h,frontMatter:()=>d,metadata:()=>s,toc:()=>c});var o=n(5893),i=n(1151);const d={},r="Remove events",s={id:"going-deeper/remove-events",title:"Remove events",description:"This is an experimental functionality. Please note that this functionality is only supported by Azure and Local providers.",source:"@site/docs/10_going-deeper/remove-events.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/remove-events",permalink:"/going-deeper/remove-events",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/remove-events.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1736258719,formattedLastUpdatedAt:"Jan 7, 2025",frontMatter:{},sidebar:"docs",previous:{title:"Booster instrumentation",permalink:"/going-deeper/instrumentation"},next:{title:"Scaling Booster Azure Functions",permalink:"/going-deeper/azure-scale"}},a={},c=[];function l(e){const t={a:"a",admonition:"admonition",code:"code",h1:"h1",li:"li",p:"p",pre:"pre",ul:"ul",...(0,i.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(t.h1,{id:"remove-events",children:"Remove events"}),"\n",(0,o.jsx)(t.admonition,{type:"warning",children:(0,o.jsx)(t.p,{children:"This is an experimental functionality. Please note that this functionality is only supported by Azure and Local providers."})}),"\n",(0,o.jsx)(t.p,{children:"Booster allows to delete past events and their related entities as to update the affected ReadModels."}),"\n",(0,o.jsxs)(t.p,{children:["By using the ",(0,o.jsx)(t.code,{children:"Booster.deleteEvent"})," command it is possible to indicate the event to be deleted. To do so, you must indicate:"]}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:["entityID: The ",(0,o.jsx)(t.code,{children:"id"})," of the entity of the event to be deleted"]}),"\n",(0,o.jsx)(t.li,{children:"entityTypeName: The entity type name of the event entity to be deleted"}),"\n",(0,o.jsx)(t.li,{children:"createdAt: The date of creation of the event."}),"\n"]}),"\n",(0,o.jsx)(t.p,{children:"Example:"}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"import { Booster, Command } from '@boostercloud/framework-core'\nimport { EventDeleteParameters } from '@boostercloud/framework-types'\n\n@Command({\n  authorize: 'all',\n})\nexport class HardDelete {\n  public constructor(readonly entityId: string, readonly entityTypeName: string, readonly createdAt: string) {}\n\n  public static async handle(command: HardDelete): Promise<boolean> {\n    const parameters: EventDeleteParameters = {\n      entityID: command.entityId,\n      entityTypeName: command.entityTypeName,\n      createdAt: command.createdAt,\n    }\n    return await Booster.deleteEvent(parameters)\n  }\n}\n"})}),"\n",(0,o.jsx)(t.p,{children:"When executing this command, Booster will update the selected event in the corresponding database with an empty value and a deletion date.\nThis way, it will be reflected in the system that there was an event that was subsequently deleted."}),"\n",(0,o.jsxs)(t.p,{children:["Deleted events are ignored by Booster, but they can be accessed using the corresponding methods (",(0,o.jsx)(t.code,{children:"eventsByEntity"})," and ",(0,o.jsx)(t.code,{children:"eventsByType"}),")."]}),"\n",(0,o.jsx)(t.p,{children:"The entities associated with a deleted event will be permanently removed from the database."}),"\n",(0,o.jsxs)(t.p,{children:["ReadModels are not automatically modified or deleted and it is up to the user to act accordingly.\nTo do so, the methods annotated with ",(0,o.jsx)(t.code,{children:"@Project"})," of the ReadModels have a third parameter ",(0,o.jsx)(t.code,{children:"unProject"})," which allows to define a\nfunction that will be executed when the entity defined in the projection and with the ",(0,o.jsx)(t.code,{children:"joinKey"})," defined in the projection is deleted."]}),"\n",(0,o.jsx)(t.p,{children:"This third parameter will be a static function with the same parameters as the method we are projecting."}),"\n",(0,o.jsxs)(t.p,{children:["It is possible to use the same method that is used for projecting to resolve the deletions by simply specifying this\nmethod as ",(0,o.jsx)(t.code,{children:"unProject"}),"."]}),"\n",(0,o.jsx)(t.p,{children:"Example:"}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"  @Projects(Pack, 'products', ProductReadModel.updateWithPack)\n  public static updateWithPack(\n    pack: Pack,\n    readModelID: UUID,\n    currentProductReadModel?: ProductReadModel,\n    projectionInfo?: ProjectionInfo\n  ): ProjectionResult<ProductReadModel> {\n    if (projectionInfo?.reason === ProjectionInfoReason.ENTITY_DELETED) {\n      return ReadModelAction.Delete\n    }\n    //   ... other code\n  }\n"})}),"\n",(0,o.jsxs)(t.p,{children:["In this case, if the ",(0,o.jsx)(t.code,{children:"Pack"})," entity with the joinKey ",(0,o.jsx)(t.code,{children:"products"})," is deleted, the ",(0,o.jsx)(t.code,{children:"updateWithPack"})," method will be executed and will include a last parameter called ",(0,o.jsx)(t.code,{children:"projectionInfo"}),".\nThis parameter contains the ",(0,o.jsx)(t.code,{children:"reason"})," field, which in this case will be set to ",(0,o.jsx)(t.code,{children:"ENTITY_DELETED"})," to indicate that the entity is being deleted."]}),"\n",(0,o.jsx)(t.p,{children:"Another option is to define your own deletion method independent of the projection method. In case of deletion the method\ncalled will be the newly defined method."}),"\n",(0,o.jsx)(t.p,{children:"Example:"}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"  @Projects(Product, 'id', ProductReadModel.unProjectWithProduct)\n  public static updateWithProduct(product: Product): ProjectionResult<ProductReadModel> {\n    // ... other code\n  }\n\n  public static unProjectWithProduct(\n    _product: Product,\n    _currentProductReadModel?: ProductReadModel,\n    _projectionInfo?: ProjectionInfo\n  ): ProjectionResult<ProductReadModel> {\n    return ReadModelAction.Delete\n  }\n"})}),"\n",(0,o.jsxs)(t.p,{children:["(",(0,o.jsx)(t.a,{href:"https://docs.boosterframework.com/architecture/read-model/#deleting-read-models",children:"See more details about how to delete a ReadModel in the docs"}),")"]}),"\n",(0,o.jsx)(t.admonition,{type:"warning",children:(0,o.jsx)(t.p,{children:"Please note that these changes are final and it is not possible to retrieve the information once they have been made."})})]})}function h(e={}){const{wrapper:t}={...(0,i.a)(),...e.components};return t?(0,o.jsx)(t,{...e,children:(0,o.jsx)(l,{...e})}):l(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>s,a:()=>r});var o=n(7294);const i={},d=o.createContext(i);function r(e){const t=o.useContext(d);return o.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function s(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(i):e.components||i:r(e.components),o.createElement(d.Provider,{value:t},e.children)}}}]);