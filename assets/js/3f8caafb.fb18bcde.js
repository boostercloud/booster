"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4296],{9930:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>a,contentTitle:()=>l,default:()=>d,frontMatter:()=>i,metadata:()=>r,toc:()=>h});var s=t(5893),o=t(1151);const i={description:"Learn how to get Booster health information"},l=void 0,r={id:"going-deeper/health/sensor-health",title:"sensor-health",description:"Learn how to get Booster health information",source:"@site/docs/10_going-deeper/health/sensor-health.md",sourceDirName:"10_going-deeper/health",slug:"/going-deeper/health/sensor-health",permalink:"/going-deeper/health/sensor-health",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/health/sensor-health.md",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1707997370,formattedLastUpdatedAt:"Feb 15, 2024",frontMatter:{description:"Learn how to get Booster health information"},sidebar:"docs",previous:{title:"Sensor",permalink:"/going-deeper/sensor"},next:{title:"Testing",permalink:"/going-deeper/testing"}},a={},h=[{value:"Health",id:"health",level:2},{value:"Supported Providers",id:"supported-providers",level:2},{value:"Enabling Health Functionality",id:"enabling-health-functionality",level:3},{value:"Health Endpoint",id:"health-endpoint",level:3},{value:"Available endpoints",id:"available-endpoints",level:4},{value:"Health Status Response",id:"health-status-response",level:3},{value:"Get specific component health information",id:"get-specific-component-health-information",level:3},{value:"Health configuration",id:"health-configuration",level:3},{value:"Booster components default configuration",id:"booster-components-default-configuration",level:4},{value:"User components configuration",id:"user-components-configuration",level:4},{value:"Create your own health endpoint",id:"create-your-own-health-endpoint",level:3},{value:"Booster health endpoints",id:"booster-health-endpoints",level:3},{value:"booster",id:"booster",level:4},{value:"booster/function",id:"boosterfunction",level:4},{value:"booster/database",id:"boosterdatabase",level:4},{value:"booster/database/events",id:"boosterdatabaseevents",level:4},{value:"booster/database/readmodels",id:"boosterdatabasereadmodels",level:4},{value:"Health status",id:"health-status",level:3},{value:"Securing health endpoints",id:"securing-health-endpoints",level:3},{value:"Example",id:"example",level:3}];function c(e){const n={a:"a",blockquote:"blockquote",code:"code",h2:"h2",h3:"h3",h4:"h4",li:"li",ol:"ol",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,o.a)(),...e.components};return(0,s.jsxs)(s.Fragment,{children:[(0,s.jsx)(n.h2,{id:"health",children:"Health"}),"\n",(0,s.jsx)(n.p,{children:"The Health functionality allows users to easily monitor the health status of their applications. With this functionality, users can make GET requests to a specific endpoint and retrieve detailed information about the health and status of their application components."}),"\n",(0,s.jsx)(n.h2,{id:"supported-providers",children:"Supported Providers"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"Azure Provider"}),"\n",(0,s.jsx)(n.li,{children:"Local Provider"}),"\n"]}),"\n",(0,s.jsx)(n.h3,{id:"enabling-health-functionality",children:"Enabling Health Functionality"}),"\n",(0,s.jsx)(n.p,{children:"To enable the Health functionality in your Booster application, follow these steps:"}),"\n",(0,s.jsxs)(n.ol,{children:["\n",(0,s.jsx)(n.li,{children:"Install or update to the latest version of the Booster framework, ensuring compatibility with the Health functionality."}),"\n",(0,s.jsx)(n.li,{children:"Enable the Booster Health endpoints in your application's configuration file. Example configuration in config.ts:"}),"\n"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"Booster.configure('local', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-local'\n  Object.values(config.sensorConfiguration.health.booster).forEach((indicator) => {\n    indicator.enabled = true\n  })\n})\n"})}),"\n",(0,s.jsx)(n.p,{children:"Or enable only the components you want:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"Booster.configure('local', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-local'\n  const sensors = config.sensorConfiguration.health.booster\n  sensors[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = true\n})\n"})}),"\n",(0,s.jsxs)(n.ol,{start:"3",children:["\n",(0,s.jsx)(n.li,{children:"Optionally, implement health checks for your application components. Each component should provide a health method that performs the appropriate checks and returns a response indicating the health status. Example:"}),"\n"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"import {\n  BoosterConfig,\n  HealthIndicatorResult,\n  HealthIndicatorMetadata,\n  HealthStatus,\n} from '@boostercloud/framework-types'\nimport { HealthSensor } from '@boostercloud/framework-core'\n\n@HealthSensor({\n  id: 'application',\n  name: 'my-application',\n  enabled: true,\n  details: true,\n  showChildren: true,\n})\nexport class ApplicationHealthIndicator {\n  public async health(\n    config: BoosterConfig,\n    healthIndicatorMetadata: HealthIndicatorMetadata\n  ): Promise<HealthIndicatorResult> {\n    return {\n      status: HealthStatus.UP,\n    } as HealthIndicatorResult\n  }\n}\n"})}),"\n",(0,s.jsxs)(n.ol,{start:"4",children:["\n",(0,s.jsx)(n.li,{children:"A health check typically involves verifying the connectivity and status of the component, running any necessary tests, and returning an appropriate status code."}),"\n",(0,s.jsxs)(n.li,{children:["Start or restart your Booster application. The Health functionality will be available at the ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/",children:"https://your-application-url/sensor/health/"})," endpoint URL."]}),"\n"]}),"\n",(0,s.jsx)(n.h3,{id:"health-endpoint",children:"Health Endpoint"}),"\n",(0,s.jsxs)(n.p,{children:["The Health functionality provides a dedicated endpoint where users can make GET requests to retrieve the health status of their application. The endpoint URL is: ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/",children:"https://your-application-url/sensor/health/"})]}),"\n",(0,s.jsxs)(n.p,{children:["This endpoint will return all the enabled Booster and application components health status. To get specific component health status, add the component status to the url. For example, to get the events status use: ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster/database/events",children:"https://your-application-url/sensor/health/booster/database/events"})]}),"\n",(0,s.jsx)(n.h4,{id:"available-endpoints",children:"Available endpoints"}),"\n",(0,s.jsx)(n.p,{children:"Booster provides the following endpoints to retrieve the enabled components:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/",children:"https://your-application-url/sensor/health/"}),": All the components status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster",children:"https://your-application-url/sensor/health/booster"}),": Booster status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster/database",children:"https://your-application-url/sensor/health/booster/database"}),": Database status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster/database/events",children:"https://your-application-url/sensor/health/booster/database/events"}),": Events status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster/database/readmodels",children:"https://your-application-url/sensor/health/booster/database/readmodels"}),": ReadModels status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/booster/function",children:"https://your-application-url/sensor/health/booster/function"}),": Functions status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/your-component-id",children:"https://your-application-url/sensor/health/your-component-id"}),": User defined status"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/your-component-id/your-component-child-id",children:"https://your-application-url/sensor/health/your-component-id/your-component-child-id"}),": User child component status"]}),"\n"]}),"\n",(0,s.jsxs)(n.p,{children:["Depending on the ",(0,s.jsx)(n.code,{children:"showChildren"})," configuration, children components will be included or not."]}),"\n",(0,s.jsx)(n.h3,{id:"health-status-response",children:"Health Status Response"}),"\n",(0,s.jsx)(n.p,{children:"Each component response will contain the following information:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: The component or subsystem status"}),"\n",(0,s.jsx)(n.li,{children:"name: component description"}),"\n",(0,s.jsx)(n.li,{children:"id: string. unique component identifier. You can request a component status using the id in the url"}),"\n",(0,s.jsxs)(n.li,{children:["details: optional object. If ",(0,s.jsx)(n.code,{children:"details"})," is true, specific details about this component."]}),"\n",(0,s.jsxs)(n.li,{children:["components: optional object. If ",(0,s.jsx)(n.code,{children:"showChildren"})," is true, children components health status."]}),"\n"]}),"\n",(0,s.jsx)(n.p,{children:"Example:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-json",children:'[\n  {\n    "status": "UP",\n    "details": {\n      "urls": [\n        "dbs/my-store-app"\n      ]\n    },\n    "name": "Booster Database",\n    "id": "booster/database",\n    "components": [\n      {\n        "status": "UP",\n        "details": {\n          "url": "dbs/my-store-app/colls/my-store-app-events-store",\n          "count": 6\n        },\n        "name": "Booster Database Events",\n        "id": "booster/database/events"\n      },\n      {\n        "status": "UP",\n        "details": [\n          {\n            "url": "dbs/my-store-app/colls/my-store-app-ProductReadModel",\n            "count": 1\n          }\n        ],\n        "name": "Booster Database ReadModels",\n        "id": "booster/database/readmodels"\n      }\n    ]\n  }\n]\n'})}),"\n",(0,s.jsx)(n.h3,{id:"get-specific-component-health-information",children:"Get specific component health information"}),"\n",(0,s.jsxs)(n.p,{children:["Use the ",(0,s.jsx)(n.code,{children:"id"})," field to get specific component health information. Booster provides the following ids:"]}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"booster"}),"\n",(0,s.jsx)(n.li,{children:"booster/function"}),"\n",(0,s.jsx)(n.li,{children:"booster/database"}),"\n",(0,s.jsx)(n.li,{children:"booster/database/events"}),"\n",(0,s.jsx)(n.li,{children:"booster/database/readmodels"}),"\n"]}),"\n",(0,s.jsx)(n.p,{children:"You can provide new components:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@HealthSensor({\n  id: 'application',\n})\n"})}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@HealthSensor({\n  id: 'application/child',\n})\n"})}),"\n",(0,s.jsx)(n.p,{children:"Add your own components to Booster:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@HealthSensor({\n  id: `${BOOSTER_HEALTH_INDICATORS_IDS.DATABASE}/extra`,\n})\n"})}),"\n",(0,s.jsx)(n.p,{children:"Or override Booster existing components with your own implementation:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@HealthSensor({\n  id: BOOSTER_HEALTH_INDICATORS_IDS.DATABASE,\n})\n"})}),"\n",(0,s.jsx)(n.h3,{id:"health-configuration",children:"Health configuration"}),"\n",(0,s.jsx)(n.p,{children:"Health components are fully configurable, allowing you to display the information you want at any moment."}),"\n",(0,s.jsx)(n.p,{children:"Configuration options:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"enabled: If false, this indicator and the components of this indicator will be skipped"}),"\n",(0,s.jsx)(n.li,{children:"details: If false, the indicator will not include the details"}),"\n",(0,s.jsxs)(n.li,{children:["showChildren: If false, this indicator will not include children components in the tree.","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"Children components will be shown through children urls"}),"\n"]}),"\n"]}),"\n",(0,s.jsxs)(n.li,{children:["authorize: Authorize configuration. ",(0,s.jsx)(n.a,{href:"https://docs.boosterframework.com/security/security",children:"See security documentation"})]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"booster-components-default-configuration",children:"Booster components default configuration"}),"\n",(0,s.jsx)(n.p,{children:"Booster sets the following default configuration for its own components:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"enabled: false"}),"\n",(0,s.jsx)(n.li,{children:"details: true"}),"\n",(0,s.jsx)(n.li,{children:"showChildren: true"}),"\n"]}),"\n",(0,s.jsxs)(n.p,{children:["Change this configuration using the ",(0,s.jsx)(n.code,{children:"config.sensorConfiguration"})," object. This object provides:"]}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"config.sensorConfiguration.health.globalAuthorizer: Allow to define authorization configuration"}),"\n",(0,s.jsxs)(n.li,{children:["config.sensorConfiguration.health.booster: Allow to override default Booster components configuration","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].enabled"}),"\n",(0,s.jsx)(n.li,{children:"config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].details"}),"\n",(0,s.jsx)(n.li,{children:"config.sensorConfiguration.health.booster[BOOSTER_COMPONENT_ID].showChildren"}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"user-components-configuration",children:"User components configuration"}),"\n",(0,s.jsxs)(n.p,{children:["Use ",(0,s.jsx)(n.code,{children:"@HealthSensor"})," parameters to configure user components. Example:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@HealthSensor({\n  id: 'user',\n  name: 'my-application',\n  enabled: true,\n  details: true,\n  showChildren: true,\n})\n"})}),"\n",(0,s.jsx)(n.h3,{id:"create-your-own-health-endpoint",children:"Create your own health endpoint"}),"\n",(0,s.jsxs)(n.p,{children:["Create your own health endpoint with a class annotated with ",(0,s.jsx)(n.code,{children:"@HealthSensor"})," decorator. This class\nshould define a ",(0,s.jsx)(n.code,{children:"health"})," method that returns a ",(0,s.jsx)(n.strong,{children:"HealthIndicatorResult"}),". Example:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"import {\n  BoosterConfig,\n  HealthIndicatorResult,\n  HealthIndicatorMetadata,\n  HealthStatus,\n} from '@boostercloud/framework-types'\nimport { HealthSensor } from '@boostercloud/framework-core'\n\n@HealthSensor({\n  id: 'application',\n  name: 'my-application',\n  enabled: true,\n  details: true,\n  showChildren: true,\n})\nexport class ApplicationHealthIndicator {\n  public async health(\n    config: BoosterConfig,\n    healthIndicatorMetadata: HealthIndicatorMetadata\n  ): Promise<HealthIndicatorResult> {\n    return {\n      status: HealthStatus.UP,\n    } as HealthIndicatorResult\n  }\n}\n"})}),"\n",(0,s.jsx)(n.h3,{id:"booster-health-endpoints",children:"Booster health endpoints"}),"\n",(0,s.jsx)(n.h4,{id:"booster",children:"booster"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: UP if and only if graphql function is UP and events are UP"}),"\n",(0,s.jsxs)(n.li,{children:["details:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"boosterVersion: Booster version number"}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"boosterfunction",children:"booster/function"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: UP if and only if graphql function is UP"}),"\n",(0,s.jsxs)(n.li,{children:["details:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"graphQL_url: GraphQL function url"}),"\n",(0,s.jsxs)(n.li,{children:["cpus: Information about each logical CPU core.","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:["cpu:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"model: Cpu model. Example: AMD EPYC 7763 64-Core Processor"}),"\n",(0,s.jsx)(n.li,{children:"speed: cpu speed in MHz"}),"\n",(0,s.jsxs)(n.li,{children:["times: The number of milliseconds the CPU/core spent in (see iostat)","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"user:  CPU utilization that occurred while executing at the user level (application)"}),"\n",(0,s.jsx)(n.li,{children:"nice: CPU utilization that occurred while executing at the user level with nice priority."}),"\n",(0,s.jsx)(n.li,{children:"sys: CPU utilization that occurred while executing at the system level (kernel)."}),"\n",(0,s.jsx)(n.li,{children:"idle: CPU or CPUs were idle and the system did not have an outstanding disk I/O request."}),"\n",(0,s.jsx)(n.li,{children:"irq: CPU load system"}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.li,{children:"timesPercentages: For each times value, the percentage over the total times"}),"\n"]}),"\n"]}),"\n",(0,s.jsxs)(n.li,{children:["memory:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"totalBytes: the total amount of system memory in bytes as an integer."}),"\n",(0,s.jsx)(n.li,{children:"freeBytes: the amount of free system memory in bytes as an integer."}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"boosterdatabase",children:"booster/database"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: UP if and only if events are UP and Read Models are UP"}),"\n",(0,s.jsxs)(n.li,{children:["details:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"urls: Database urls"}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"boosterdatabaseevents",children:"booster/database/events"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: UP if and only if events are UP"}),"\n",(0,s.jsxs)(n.li,{children:["details:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.strong,{children:"AZURE PROVIDER"}),":","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"url: Events url"}),"\n",(0,s.jsx)(n.li,{children:"count: number of rows"}),"\n"]}),"\n"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.strong,{children:"LOCAL PROVIDER"}),":","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"file: event database file"}),"\n",(0,s.jsx)(n.li,{children:"count: number of rows"}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.h4,{id:"boosterdatabasereadmodels",children:"booster/database/readmodels"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"status: UP if and only if Read Models are UP"}),"\n",(0,s.jsxs)(n.li,{children:["details:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.strong,{children:"AZURE PROVIDER"}),":","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:["For each Read Model:","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"url: Event url"}),"\n",(0,s.jsx)(n.li,{children:"count: number of rows"}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.strong,{children:"LOCAL PROVIDER"}),":","\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"file: Read Models database file"}),"\n",(0,s.jsx)(n.li,{children:"count: number of total rows"}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n"]}),"\n",(0,s.jsxs)(n.blockquote,{children:["\n",(0,s.jsxs)(n.p,{children:[(0,s.jsx)(n.strong,{children:"Note"}),": details will be included only if ",(0,s.jsx)(n.code,{children:"details"})," is enabled"]}),"\n"]}),"\n",(0,s.jsx)(n.h3,{id:"health-status",children:"Health status"}),"\n",(0,s.jsx)(n.p,{children:"Available status are"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"UP: The component or subsystem is working as expected"}),"\n",(0,s.jsx)(n.li,{children:"DOWN: The component is not working"}),"\n",(0,s.jsx)(n.li,{children:"OUT_OF_SERVICE: The component is out of service temporarily"}),"\n",(0,s.jsx)(n.li,{children:"UNKNOWN: The component state is unknown"}),"\n"]}),"\n",(0,s.jsx)(n.p,{children:"If a component throw an exception the status will be DOWN"}),"\n",(0,s.jsx)(n.h3,{id:"securing-health-endpoints",children:"Securing health endpoints"}),"\n",(0,s.jsxs)(n.p,{children:["To configure the health endpoints authorization use ",(0,s.jsx)(n.code,{children:"config.sensorConfiguration.health.globalAuthorizer"}),"."]}),"\n",(0,s.jsx)(n.p,{children:"Example:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"config.sensorConfiguration.health.globalAuthorizer = {\n      authorize: 'all',\n}\n"})}),"\n",(0,s.jsx)(n.p,{children:"If the authorization process fails, the health endpoint will return a 401 error code"}),"\n",(0,s.jsx)(n.h3,{id:"example",children:"Example"}),"\n",(0,s.jsx)(n.p,{children:"If all components are enable and showChildren is set to true:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:["A Request to ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/",children:"https://your-application-url/sensor/health/"})," will return:"]}),"\n"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"\u251c\u2500\u2500 booster\n\u2502\xa0\xa0\u251c\u2500\u2500 database\n\u2502\xa0\xa0\xa0\xa0\u251c\u2500\u2500 events\n\u2502\xa0\xa0\xa0\xa0\u2514\u2500\u2500 readmodels\n\u2514\xa0\xa0\u2514\u2500\u2500 function\n"})}),"\n",(0,s.jsx)(n.p,{children:"If the database component is disabled, the same url will return:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"\u251c\u2500\u2500 booster\n\u2514\xa0\xa0\u2514\u2500\u2500 function\n"})}),"\n",(0,s.jsxs)(n.p,{children:["If the request url is ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/database",children:"https://your-application-url/sensor/health/database"}),", the component will not be returned"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"[Empty]\n"})}),"\n",(0,s.jsxs)(n.p,{children:["And the children components will be disabled too using direct url ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/database/events",children:"https://your-application-url/sensor/health/database/events"})]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"[Empty]\n"})}),"\n",(0,s.jsxs)(n.p,{children:["If database is enabled and showChildren is set to false and using ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/",children:"https://your-application-url/sensor/health/"})]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"\u251c\u2500\u2500 booster\n\u2502\xa0\xa0\u251c\u2500\u2500 database\n\u2502\xa0\xa0\u2514\u2500\u2500 function\n"})}),"\n",(0,s.jsxs)(n.p,{children:["using ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/database",children:"https://your-application-url/sensor/health/database"}),", children will not be visible"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"\u2514\u2500\u2500 database\n"})}),"\n",(0,s.jsxs)(n.p,{children:["but you can access to them using the component url ",(0,s.jsx)(n.a,{href:"https://your-application-url/sensor/health/database/events",children:"https://your-application-url/sensor/health/database/events"})]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"\u2514\u2500\u2500 events\n"})})]})}function d(e={}){const{wrapper:n}={...(0,o.a)(),...e.components};return n?(0,s.jsx)(n,{...e,children:(0,s.jsx)(c,{...e})}):c(e)}},1151:(e,n,t)=>{t.d(n,{Z:()=>r,a:()=>l});var s=t(7294);const o={},i=s.createContext(o);function l(e){const n=s.useContext(i);return s.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function r(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:l(e.components),s.createElement(i.Provider,{value:n},e.children)}}}]);