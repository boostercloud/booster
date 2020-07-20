export const template = `<policies>
    <inbound>
        <base/>
        <set-backend-service base-url="https://{{functionAppName}}.azurewebsites.net/api"/>
    </inbound>
    <backend>
        <base/>
    </backend>
    <outbound>  
        <base/>
    </outbound>
    <on-error>
        <base/>
    </on-error>
</policies>`
