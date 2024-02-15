"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[8708],{8129:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>s,default:()=>u,frontMatter:()=>i,metadata:()=>r,toc:()=>d});var o=n(5893),a=n(1151);const i={description:"Learn how to update entities and ReadModels"},s="TouchEntities",r={id:"going-deeper/touch-entities",title:"TouchEntities",description:"Learn how to update entities and ReadModels",source:"@site/docs/10_going-deeper/touch-entities.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/touch-entities",permalink:"/going-deeper/touch-entities",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/touch-entities.mdx",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1707997370,formattedLastUpdatedAt:"Feb 15, 2024",frontMatter:{description:"Learn how to update entities and ReadModels"},sidebar:"docs",previous:{title:"Migrations",permalink:"/going-deeper/data-migrations"},next:{title:"Customizing CLI resource templates",permalink:"/going-deeper/custom-templates"}},c={},d=[];function l(e){const t={code:"code",h1:"h1",p:"p",pre:"pre",...(0,a.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(t.h1,{id:"touchentities",children:"TouchEntities"}),"\n",(0,o.jsx)(t.p,{children:"Booster provides a way to refresh the value of an entity and update the corresponding ReadModels that depend on it.\nThis functionality is useful when a new projection is added to a ReadModel and you want to apply it retroactively to the events that have already occurred.\nIt is also helpful when there was an error when calculating a ReadModel or when the snapshot of an entity was not generated."}),"\n",(0,o.jsxs)(t.p,{children:["To migrate an existing entity to a new version, you need to call ",(0,o.jsx)(t.code,{children:"BoosterTouchEntityHandler.touchEntity"})," to touch entities.\nFor example, this command will touch all the entities of the class Cart.:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"import { Booster, BoosterTouchEntityHandler, Command } from '@boostercloud/framework-core'\nimport { Register } from '@boostercloud/framework-types'\nimport { Cart } from '../entities/cart'\n\n@Command({\n  authorize: 'all',\n})\nexport class TouchCommand {\n  public constructor() {}\n\n  public static async handle(_command: TouchCommand, _register: Register): Promise<void> {\n    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)\n    const paginatedEntityIdResults = entitiesIdsResult.items\n    const carts = await Promise.all(\n      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))\n    )\n    if (!carts || carts.length === 0) {\n      return\n    }\n    await Promise.all(\n      carts.map(async (cart) => {\n        const validCart = cart!\n        await BoosterTouchEntityHandler.touchEntity('Cart', validCart.id)\n        console.log('Touched', validCart)\n        return validCart.id\n      })\n    )\n  }\n}\n"})}),"\n",(0,o.jsx)(t.p,{children:"Please note that touching entities is an advanced feature that should be used with caution and only when necessary.\nIt may affect your application performance and consistency if not used properly."})]})}function u(e={}){const{wrapper:t}={...(0,a.a)(),...e.components};return t?(0,o.jsx)(t,{...e,children:(0,o.jsx)(l,{...e})}):l(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>r,a:()=>s});var o=n(7294);const a={},i=o.createContext(a);function s(e){const t=o.useContext(i);return o.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function r(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:s(e.components),o.createElement(i.Provider,{value:t},e.children)}}}]);