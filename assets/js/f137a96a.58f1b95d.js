"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4255],{2612:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>d,contentTitle:()=>c,default:()=>m,frontMatter:()=>i,metadata:()=>l,toc:()=>h});var r=n(5893),o=n(1151),s=n(5162),a=n(4866);const i={describe:"Authorization mechanisms to access Booster Commands and Read Models"},c="Authorization",l={id:"security/authorization",title:"Authorization",description:"Booster uses a whitelisting approach to authorize users to perform commands and read models. This means that you must explicitly specify which users are allowed to perform each action. In order to do that you must configure the authorize policy parameter on every Command or Read Model. This parameter accepts one of the following options:",source:"@site/docs/04_security/02_authorization.mdx",sourceDirName:"04_security",slug:"/security/authorization",permalink:"/security/authorization",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/04_security/02_authorization.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1722356865,formattedLastUpdatedAt:"Jul 30, 2024",sidebarPosition:2,frontMatter:{describe:"Authorization mechanisms to access Booster Commands and Read Models"},sidebar:"docs",previous:{title:"Authentication",permalink:"/security/authentication"},next:{title:"GraphQL API",permalink:"/graphql"}},d={},h=[{value:"Making commands and read models public",id:"making-commands-and-read-models-public",level:2},{value:"Simple Role-based authorization",id:"simple-role-based-authorization",level:2},{value:"Defining @Roles",id:"defining-roles",level:3},{value:"Protecting commands and read models with roles",id:"protecting-commands-and-read-models-with-roles",level:3},{value:"Associating users with roles",id:"associating-users-with-roles",level:3},{value:"Extended roles using the Authentication Booster Rocket for AWS",id:"extended-roles-using-the-authentication-booster-rocket-for-aws",level:3},{value:"Custom authorization functions",id:"custom-authorization-functions",level:2},{value:"Command Authorizers",id:"command-authorizers",level:3},{value:"Read Model Authorizers",id:"read-model-authorizers",level:3},{value:"Event Stream Authorizers",id:"event-stream-authorizers",level:3}];function u(e){const t={a:"a",admonition:"admonition",code:"code",h1:"h1",h2:"h2",h3:"h3",li:"li",p:"p",pre:"pre",strong:"strong",table:"table",tbody:"tbody",td:"td",th:"th",thead:"thead",tr:"tr",ul:"ul",...(0,o.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"authorization",children:"Authorization"}),"\n",(0,r.jsxs)(t.p,{children:["Booster uses a whitelisting approach to authorize users to perform commands and read models. This means that you must explicitly specify which users are allowed to perform each action. In order to do that you must configure the ",(0,r.jsx)(t.code,{children:"authorize"})," policy parameter on every Command or Read Model. This parameter accepts one of the following options:"]}),"\n",(0,r.jsxs)(t.ul,{children:["\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.code,{children:"'all'"}),": The command or read-model is explicitly public, any user can access it."]}),"\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.code,{children:"[Role1, Role2, ...]"}),": An array of authorized ",(0,r.jsx)(t.a,{href:"#defining-roles",children:"Roles"}),", this means that only those authenticated users that have any of the roles listed there are authorized to execute the command."]}),"\n",(0,r.jsxs)(t.li,{children:["An authorizer function that matches the ",(0,r.jsx)(t.code,{children:"CommandAuthorizer"})," interface for commands or the ",(0,r.jsx)(t.code,{children:"ReadModelAuthorizer"})," interface for read models."]}),"\n"]}),"\n",(0,r.jsx)(t.h2,{id:"making-commands-and-read-models-public",children:"Making commands and read models public"}),"\n",(0,r.jsxs)(t.p,{children:["Setting the option ",(0,r.jsx)(t.code,{children:"authorize: 'all'"})," in a command or read model will make it publicly accessible to anyone that has access to the GraphQL endpoint. For example, the following command can be executed by anyone, even if they don't provide a valid JWT token:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/commands/create-comment.ts"',children:"@Command({\n  authorize: 'all',\n})\nexport class CreateComment {\n  ...\n}\n"})}),"\n",(0,r.jsx)(t.admonition,{type:"danger",children:(0,r.jsxs)(t.p,{children:[(0,r.jsx)(t.strong,{children:"Think twice if you really need fully open GraphQL endpoints in your application"}),", this might be useful during development, but we recommend to ",(0,r.jsx)(t.strong,{children:"avoid exposing your endpoints in this way in production"}),". Even for public APIs, it might be useful to issue API keys to avoid abuse. Booster is designed to scale to any given demand, but scaling also increases the cloud bill! (See ",(0,r.jsx)(t.a,{href:"https://www.sciencedirect.com/science/article/pii/S221421262100079X",children:"Denial of wallet attacks"}),")"]})}),"\n",(0,r.jsx)(t.h2,{id:"simple-role-based-authorization",children:"Simple Role-based authorization"}),"\n",(0,r.jsxs)(t.p,{children:["Booster provides a simple role-based authentication mechanism that will work in many standard scenarios. It is based on the concept of roles, which are just a set of permissions. For example, a ",(0,r.jsx)(t.code,{children:"User"})," role might have the permission to ",(0,r.jsx)(t.code,{children:"create"})," and ",(0,r.jsx)(t.code,{children:"read"})," comments, while an ",(0,r.jsx)(t.code,{children:"Admin"})," role might have the permission to ",(0,r.jsx)(t.code,{children:"create"}),", ",(0,r.jsx)(t.code,{children:"read"}),", and ",(0,r.jsx)(t.code,{children:"delete"})," comments. You can define as many roles as you want, and then assign them to users."]}),"\n",(0,r.jsx)(t.h3,{id:"defining-roles",children:"Defining @Roles"}),"\n",(0,r.jsxs)(t.p,{children:["As many other Booster artifacts, Booster Roles are defined as simple decorated classes. We recommend them to be defined in the ",(0,r.jsx)(t.code,{children:"src/config/roles.ts"})," file, but it is not limited to that file. To define a role, you only need to decorate an empty class with the ",(0,r.jsx)(t.code,{children:"@Role"})," decorator as follows:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/roles.ts"',children:"@Role()\nexport class User {}\n\n@Role()\nexport class Admin {}\n"})}),"\n",(0,r.jsx)(t.h3,{id:"protecting-commands-and-read-models-with-roles",children:"Protecting commands and read models with roles"}),"\n",(0,r.jsxs)(t.p,{children:["Once you have defined your roles, you can use them to protect your commands and read models. For example, the following command can only be executed by users that have the role ",(0,r.jsx)(t.code,{children:"Admin"}),":"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/commands/create-comment.ts"',children:"@Command({\n  authorize: [Admin],\n})\nexport class CreateComment {\n  ...\n}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["This command will not be available to users with the role ",(0,r.jsx)(t.code,{children:"User"}),"."]}),"\n",(0,r.jsx)(t.h3,{id:"associating-users-with-roles",children:"Associating users with roles"}),"\n",(0,r.jsxs)(t.p,{children:["Booster will read the roles from the JWT token that you provide in the request. The token must include a claim with the key you specidied in the ",(0,r.jsx)(t.code,{children:"rolesClaim"})," field. Booster will read such field and compare it with the declared ones in the ",(0,r.jsx)(t.code,{children:"authorize"})," field of the protected command or read model."]}),"\n",(0,r.jsx)(t.p,{children:"For example, given the following setup:"}),"\n",(0,r.jsxs)(a.Z,{groupId:"auth-roles-example",children:[(0,r.jsx)(s.Z,{value:"booster-config",label:"Booster Config",default:!0,children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/config.ts"',children:"Booster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-x'\n  config.tokenVerifiers = [\n    new JwksUriTokenVerifier(\n      'https://my-auth0-tenant.auth0.com/', // Issuer\n      'https://my-auth0-tenant.auth0.com/.well-known/jwks.json', // JWKS URL\n      // highlight-next-line\n      'firebase:groups' // <- roles are read from 'firebase:groups' claim from the token\n    ),\n  ]\n})\n"})})}),(0,r.jsx)(s.Z,{value:"decoded-token",label:"Decoded Token",children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-json",children:'{\n  // highlight-next-line\n  "firebase:groups": "User", // <- roles are read from \'firebase:groups\' claim\n  "iss": "https://securetoken.google.com/demoapp",\n  "aud": "demoapp",\n  "auth_time": 1604676721,\n  "user_id": "xJY5Y6fTbVggNtDjaNh7cNSBd7q1",\n  "sub": "xJY5Y6fTbVggNtDjaNh7cNSBd7q1",\n  "iat": 1604676721,\n  "exp": 1604680321,\n  "phone_number": "+999999999",\n  "firebase": {}\n}\n'})})}),(0,r.jsx)(s.Z,{value:"booster-command",label:"Booster Command",children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/commands/create-comment.ts"',children:"@Command({\n  authorize: [Admin],\n})\nexport class CreateComment {\n  ...\n}\n"})})}),(0,r.jsx)(s.Z,{value:"booster-roles",label:"Booster Roles",children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",metastring:'title="src/config/roles.ts"',children:"@Role()\nexport class User {}\n\n@Role()\nexport class Admin {}\n"})})})]}),"\n",(0,r.jsxs)(t.p,{children:["Booster will check that the token contains the ",(0,r.jsx)(t.code,{children:"firebase:groups"})," claim and that it contains the ",(0,r.jsx)(t.code,{children:"Admin"})," role.\nAlso, if the token doesn't contain the ",(0,r.jsx)(t.code,{children:"Admin"})," role, the command will not be executed. As you can see, the decoded token\nhas ",(0,r.jsx)(t.code,{children:"User"})," as value of the ",(0,r.jsx)(t.code,{children:"firebase:groups"})," claim, so the command will not be executed."]}),"\n",(0,r.jsxs)(t.h3,{id:"extended-roles-using-the-authentication-booster-rocket-for-aws",children:["Extended roles using the ",(0,r.jsx)(t.a,{href:"https://github.com/boostercloud/rocket-auth-aws-infrastructure",children:"Authentication Booster Rocket for AWS"})]}),"\n",(0,r.jsx)(t.p,{children:"The Authentication Rocket for AWS is an opinionated implementation of a JWT tokens issuer on top of AWS Cognito that includes out-of-the-box features like\nsign-up, sign-in, passwordless tokens, change password and many other features. When a user goes through the sign up and sign in mecanisms provided by the rocket,\nthey'll get a standard JWT access token that can be included in any request as a Bearer token and will work in the same way as any other JWT token."}),"\n",(0,r.jsxs)(t.p,{children:["When you use this rocket, you can use extra configuration parameters in the ",(0,r.jsx)(t.code,{children:"@Role"})," decorator to enable some of these features. In the following example we define ",(0,r.jsx)(t.code,{children:"Admin"}),", ",(0,r.jsx)(t.code,{children:"User"}),", ",(0,r.jsx)(t.code,{children:"SuperUser"})," and ",(0,r.jsx)(t.code,{children:"SuperUserWithoutConfirmation"})," roles. They all contain an extra ",(0,r.jsx)(t.code,{children:"auth"})," configuration attribute that set the behavior of the authorization role for each role:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"@Role({\n  auth: {\n    signUpMethods: [], // Using an empty array here prevents sign-ups (Admin has no special treatment. If you don't enable signup, you'll need to create the first admin manually in the AWS console)\n  },\n})\nexport class Admin {}\n\n@Role({\n  auth: {\n    signUpMethods: ['email'], // Enable email sign-ups for Users\n  },\n})\nexport class User {}\n\n@Role({\n  auth: {\n    signUpMethods: ['email', 'phone'], // Can sign up by email or phone\n    skipConfirmation: false, // It requires email or phone confirmation. The rocket will send either an email or a SMS with a confirmation link.\n  },\n})\nexport class SuperUser {}\n\n@Role({\n  auth: {\n    signUpMethods: ['email', 'phone'],\n    skipConfirmation: true, // It doesn't require email or phone confirmation\n  },\n})\nexport class SuperUserWithoutConfirmation {}\n"})}),"\n",(0,r.jsxs)(t.p,{children:["To learn more about the Authorization rocket for AWS, please read the ",(0,r.jsx)(t.a,{href:"https://github.com/boostercloud/rocket-auth-aws-infrastructure/blob/main/README.md",children:"README"})," in its Github repository."]}),"\n",(0,r.jsx)(t.h2,{id:"custom-authorization-functions",children:"Custom authorization functions"}),"\n",(0,r.jsxs)(t.p,{children:["Booster also allows you to implement your own authorization functions, in case the role-based authorization model doesn't work for your application. In order to\napply your own authorization functions, you need to provide them in the ",(0,r.jsx)(t.code,{children:"authorize"})," field of the command or read model. As authorization functions are regular\nJavaScript functions, you can easily reuse them in your project or even in other Booster projects as a library."]}),"\n",(0,r.jsx)(t.h3,{id:"command-authorizers",children:"Command Authorizers"}),"\n",(0,r.jsxs)(t.p,{children:["As mentioned, the ",(0,r.jsx)(t.code,{children:"authorize"})," parameter of the ",(0,r.jsx)(t.code,{children:"@Command"})," can receive a function. However, this function must match the ",(0,r.jsx)(t.code,{children:"CommandAuthorizer"})," type. This function will receive two parameters and return a ",(0,r.jsx)(t.code,{children:"Promise"})," that will resolve if the user is authorized to execute the command or reject if not:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"export type CommandAuthorizer = (currentUser?: UserEnvelope, input?: CommandInput) => Promise<void>\n"})}),"\n",(0,r.jsxs)(t.table,{children:[(0,r.jsx)(t.thead,{children:(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.th,{children:"Parameter"}),(0,r.jsx)(t.th,{children:"Type"}),(0,r.jsx)(t.th,{children:"Description"})]})}),(0,r.jsxs)(t.tbody,{children:[(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:"currentUser"}),(0,r.jsx)(t.td,{children:(0,r.jsx)(t.code,{children:"UserEnvelope"})}),(0,r.jsx)(t.td,{children:"User data decoded from the provided token"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:"input"}),(0,r.jsx)(t.td,{children:(0,r.jsx)(t.code,{children:"CommandInput"})}),(0,r.jsx)(t.td,{children:"The input of the command"})]})]})]}),"\n",(0,r.jsxs)(t.p,{children:["For instance, if you want to restrict a command to users that have a permission named ",(0,r.jsx)(t.code,{children:"Permission-To-Rock"})," in the ",(0,r.jsx)(t.code,{children:"permissions"})," claim you can do this:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"\nconst CustomCommandAuthorizer: CommandAuthorizer = async (currentUser) => {\n    if (!currentUser.claims['permissions'].includes('Permission-To-Rock')) {\n      throw new Error(`User ${currentUser.username} should not be rocking!`) // <- This will reject the access to the command\n    }\n  }\n\n@Command({\n  authorize: CustomCommandAuthorizer,\n})\nexport class PerformIncredibleGuitarSolo {\n  ...\n}\n"})}),"\n",(0,r.jsx)(t.h3,{id:"read-model-authorizers",children:"Read Model Authorizers"}),"\n",(0,r.jsxs)(t.p,{children:["As with commands, the ",(0,r.jsx)(t.code,{children:"authorize"})," parameter of the ",(0,r.jsx)(t.code,{children:"@ReadModel"})," decorator can also receive a function. However, this function must match the ",(0,r.jsx)(t.code,{children:"ReadModelAuthorizer"})," type. This function will receive two parameters and return a ",(0,r.jsx)(t.code,{children:"Promise"})," that will resolve if the user is authorized to execute the command or reject if not:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"export type ReadModelAuthorizer<TReadModel extends ReadModelInterface> = (\n  currentUser?: UserEnvelope,\n  readModelRequestEnvelope?: ReadModelRequestEnvelope<TReadModel>\n) => Promise<void>\n"})}),"\n",(0,r.jsxs)(t.table,{children:[(0,r.jsx)(t.thead,{children:(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.th,{children:"Parameter"}),(0,r.jsx)(t.th,{children:"Type"}),(0,r.jsx)(t.th,{children:"Description"})]})}),(0,r.jsxs)(t.tbody,{children:[(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:"currentUser"}),(0,r.jsx)(t.td,{children:(0,r.jsx)(t.code,{children:"UserEnvelope"})}),(0,r.jsx)(t.td,{children:"User data decoded from the provided token"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:"input"}),(0,r.jsx)(t.td,{children:(0,r.jsx)(t.code,{children:"CommandInput"})}),(0,r.jsx)(t.td,{children:"The input of the command"})]})]})]}),"\n",(0,r.jsx)(t.p,{children:"For instance, you may want to restrict access to a specific resource only to people that has been granted read permission:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"const CustomReadModelAuthorizer: ReadModelAuthorizer = async (currentUser, readModelRequestEnvelope) => {\n    const userPermissions = Booster.entity(UserPermissions, currentUser.username)\n    if (!userPermissions || !userPermissions.accessTo[readModelRequestEnvelope.className].includes(readModelRequestEnvelope.key.id)) {\n      throw new Error(`User ${currentUser.username} should not be looking here`)\n    }\n  }\n\n@ReadModel({\n  authorize: CustomReadModelAuthorizer\n})\n"})}),"\n",(0,r.jsx)(t.h3,{id:"event-stream-authorizers",children:"Event Stream Authorizers"}),"\n",(0,r.jsxs)(t.p,{children:["You can restrict the access to the ",(0,r.jsx)(t.a,{href:"/features/event-stream",children:"Event Stream"})," of an ",(0,r.jsx)(t.code,{children:"Entity"})," by providing an ",(0,r.jsx)(t.code,{children:"authorizeReadEvents"})," function in the ",(0,r.jsx)(t.code,{children:"@Entity"})," decorator. This function is called every time an event stream is requested. The function must match the ",(0,r.jsx)(t.code,{children:"EventStreamAuthorizer"})," type receives the current user and the event search request as parameters. The function must return a ",(0,r.jsx)(t.code,{children:"Promise<void>"}),". If the promise is rejected, the request will be denied. If the promise is resolved successfully, the request will be allowed."]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"export type EventStreamAuthorizer = (\n  currentUser?: UserEnvelope,\n  eventSearchRequest?: EventSearchRequest\n) => Promise<void>\n"})}),"\n",(0,r.jsx)(t.p,{children:"For instance, you can restrict access to entities that the current user own."}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"const CustomEventAuthorizer: EventStreamAuthorizer = async (currentUser, eventSearchRequest) => {\n  const { entityID } = eventSearchRequest.parameters\n  if (!entityID) {\n    throw new Error(`${currentUser.username} cannot list carts`)\n  }\n  const cart = Booster.entity(Cart, entityID)\n  if (cart.ownerUserName !== currentUser.userName) {\n    throw new Error(`${currentUser.username} cannot see events in cart ${entityID}`)\n  }\n}\n\n\n@Entity({\n  authorizeReadEvents: CustomEventAuthorizer\n})\nexport class Cart {\n  public constructor(\n    readonly id: UUID,\n    readonly ownerUserName: string,\n    readonly cartItems: Array<CartItem>,\n    public shippingAddress?: Address,\n    public checks = 0\n  ) {}\n  ...\n}\n"})})]})}function m(e={}){const{wrapper:t}={...(0,o.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(u,{...e})}):u(e)}},5162:(e,t,n)=>{n.d(t,{Z:()=>a});n(7294);var r=n(512);const o={tabItem:"tabItem_Ymn6"};var s=n(5893);function a(e){let{children:t,hidden:n,className:a}=e;return(0,s.jsx)("div",{role:"tabpanel",className:(0,r.Z)(o.tabItem,a),hidden:n,children:t})}},4866:(e,t,n)=>{n.d(t,{Z:()=>w});var r=n(7294),o=n(512),s=n(2466),a=n(6550),i=n(469),c=n(1980),l=n(7392),d=n(12);function h(e){return r.Children.toArray(e).filter((e=>"\n"!==e)).map((e=>{if(!e||(0,r.isValidElement)(e)&&function(e){const{props:t}=e;return!!t&&"object"==typeof t&&"value"in t}(e))return e;throw new Error(`Docusaurus error: Bad <Tabs> child <${"string"==typeof e.type?e.type:e.type.name}>: all children of the <Tabs> component should be <TabItem>, and every <TabItem> should have a unique "value" prop.`)}))?.filter(Boolean)??[]}function u(e){const{values:t,children:n}=e;return(0,r.useMemo)((()=>{const e=t??function(e){return h(e).map((e=>{let{props:{value:t,label:n,attributes:r,default:o}}=e;return{value:t,label:n,attributes:r,default:o}}))}(n);return function(e){const t=(0,l.l)(e,((e,t)=>e.value===t.value));if(t.length>0)throw new Error(`Docusaurus error: Duplicate values "${t.map((e=>e.value)).join(", ")}" found in <Tabs>. Every value needs to be unique.`)}(e),e}),[t,n])}function m(e){let{value:t,tabValues:n}=e;return n.some((e=>e.value===t))}function p(e){let{queryString:t=!1,groupId:n}=e;const o=(0,a.k6)(),s=function(e){let{queryString:t=!1,groupId:n}=e;if("string"==typeof t)return t;if(!1===t)return null;if(!0===t&&!n)throw new Error('Docusaurus error: The <Tabs> component groupId prop is required if queryString=true, because this value is used as the search param name. You can also provide an explicit value such as queryString="my-search-param".');return n??null}({queryString:t,groupId:n});return[(0,c._X)(s),(0,r.useCallback)((e=>{if(!s)return;const t=new URLSearchParams(o.location.search);t.set(s,e),o.replace({...o.location,search:t.toString()})}),[s,o])]}function f(e){const{defaultValue:t,queryString:n=!1,groupId:o}=e,s=u(e),[a,c]=(0,r.useState)((()=>function(e){let{defaultValue:t,tabValues:n}=e;if(0===n.length)throw new Error("Docusaurus error: the <Tabs> component requires at least one <TabItem> children component");if(t){if(!m({value:t,tabValues:n}))throw new Error(`Docusaurus error: The <Tabs> has a defaultValue "${t}" but none of its children has the corresponding value. Available values are: ${n.map((e=>e.value)).join(", ")}. If you intend to show no default tab, use defaultValue={null} instead.`);return t}const r=n.find((e=>e.default))??n[0];if(!r)throw new Error("Unexpected error: 0 tabValues");return r.value}({defaultValue:t,tabValues:s}))),[l,h]=p({queryString:n,groupId:o}),[f,x]=function(e){let{groupId:t}=e;const n=function(e){return e?`docusaurus.tab.${e}`:null}(t),[o,s]=(0,d.Nk)(n);return[o,(0,r.useCallback)((e=>{n&&s.set(e)}),[n,s])]}({groupId:o}),j=(()=>{const e=l??f;return m({value:e,tabValues:s})?e:null})();(0,i.Z)((()=>{j&&c(j)}),[j]);return{selectedValue:a,selectValue:(0,r.useCallback)((e=>{if(!m({value:e,tabValues:s}))throw new Error(`Can't select invalid tab value=${e}`);c(e),h(e),x(e)}),[h,x,s]),tabValues:s}}var x=n(2389);const j={tabList:"tabList__CuJ",tabItem:"tabItem_LNqP"};var g=n(5893);function b(e){let{className:t,block:n,selectedValue:r,selectValue:a,tabValues:i}=e;const c=[],{blockElementScrollPositionUntilNextRender:l}=(0,s.o5)(),d=e=>{const t=e.currentTarget,n=c.indexOf(t),o=i[n].value;o!==r&&(l(t),a(o))},h=e=>{let t=null;switch(e.key){case"Enter":d(e);break;case"ArrowRight":{const n=c.indexOf(e.currentTarget)+1;t=c[n]??c[0];break}case"ArrowLeft":{const n=c.indexOf(e.currentTarget)-1;t=c[n]??c[c.length-1];break}}t?.focus()};return(0,g.jsx)("ul",{role:"tablist","aria-orientation":"horizontal",className:(0,o.Z)("tabs",{"tabs--block":n},t),children:i.map((e=>{let{value:t,label:n,attributes:s}=e;return(0,g.jsx)("li",{role:"tab",tabIndex:r===t?0:-1,"aria-selected":r===t,ref:e=>c.push(e),onKeyDown:h,onClick:d,...s,className:(0,o.Z)("tabs__item",j.tabItem,s?.className,{"tabs__item--active":r===t}),children:n??t},t)}))})}function y(e){let{lazy:t,children:n,selectedValue:o}=e;const s=(Array.isArray(n)?n:[n]).filter(Boolean);if(t){const e=s.find((e=>e.props.value===o));return e?(0,r.cloneElement)(e,{className:"margin-top--md"}):null}return(0,g.jsx)("div",{className:"margin-top--md",children:s.map(((e,t)=>(0,r.cloneElement)(e,{key:t,hidden:e.props.value!==o})))})}function v(e){const t=f(e);return(0,g.jsxs)("div",{className:(0,o.Z)("tabs-container",j.tabList),children:[(0,g.jsx)(b,{...e,...t}),(0,g.jsx)(y,{...e,...t})]})}function w(e){const t=(0,x.Z)();return(0,g.jsx)(v,{...e,children:h(e.children)},String(t))}},1151:(e,t,n)=>{n.d(t,{Z:()=>i,a:()=>a});var r=n(7294);const o={},s=r.createContext(o);function a(e){const t=r.useContext(s);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function i(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:a(e.components),r.createElement(s.Provider,{value:t},e.children)}}}]);