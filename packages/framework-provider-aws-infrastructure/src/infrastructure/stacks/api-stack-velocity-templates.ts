export const CognitoTemplates = {
  signUp: {
    // This template is a bit more complex because we are transforming the attribute 'roles' from an array to a
    // comma-separated string
    request: `#set($root = $input.path('$'))
             {
               "ClientId": "$root.clientId",
               "Username": "$root.username",
               "Password": "$root.password",
               "UserAttributes": [
                 #foreach($attributeKey in $root.userAttributes.keySet())
                     #if($attributeKey == "roles")
                         #set($attributeName = "custom:roles")
                         #set($attributeValue = "#foreach($rol in $root.userAttributes.get('roles'))$rol#if($foreach.hasNext),#end#end")
                     #else
                         #set($attributeName = $attributeKey)
                         #set($attributeValue = $root.userAttributes.get($attributeKey))
                     #end
                     {
                       "Name": "$attributeName",
                       "Value": "$attributeValue"
                     }#if($foreach.hasNext),#end
                 #end
                 ]
             }`,
    response: '{}',
  },
  signIn: {
    request: `#set($root = $input.path('$'))
              {
                "ClientId":"$root.clientId",
                "AuthFlow": "USER_PASSWORD_AUTH",
                "AuthParameters": { 
                  "USERNAME" : "$root.username",
                  "PASSWORD" : "$root.password" 
                }
              }`,
    response: `#set($root = $input.path('$'))
               {
                   "accessToken": "$root.AuthenticationResult.AccessToken",
                   "expiresIn": "$root.AuthenticationResult.ExpiresIn",
                   "refreshToken": "$root.AuthenticationResult.RefreshToken",
                   "tokenType": "$root.AuthenticationResult.TokenType"
               }`,
  },
  refreshToken: {
    request: `#set($root = $input.path('$'))
              {
                "ClientId":"$root.clientId",
                "AuthFlow": "REFRESH_TOKEN_AUTH",
                "AuthParameters": { 
                  "REFRESH_TOKEN" : "$root.refreshToken"
                }
              }`,
    response: `#set($root = $input.path('$'))
               {
                   "accessToken": "$root.AuthenticationResult.AccessToken",
                   "expiresIn": "$root.AuthenticationResult.ExpiresIn",
                   "refreshToken": "$root.AuthenticationResult.RefreshToken",
                   "tokenType": "$root.AuthenticationResult.TokenType"
               }`,
  },
  signOut: {
    request: `#set($root = $input.path('$'))
              {
                "AccessToken":"$root.accessToken" 
              }`,
    response: '{}',
  },
} as const
