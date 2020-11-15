export const CognitoTemplates = {
  signUp: {
    request: `#set($root = $input.path('$'))
             {
               "ClientId": "$root.clientId",
               "Username": "$root.username",
               "Password": "$root.password",
               "UserAttributes": [
                 #foreach($attributeKey in $root.userAttributes.keySet())
                     #if($attributeKey == "role")
                         #set($attributeName = "custom:role")
                         #set($attributeValue = $root.userAttributes.get('role'))
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
  confirmSignUp: {
    request: `#set($root = $input.path('$'))
             {
               "ClientId": "$root.clientId",
               "ConfirmationCode": "$root.confirmationCode",
               "Username": "$root.username"
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
                   "idToken": "$root.AuthenticationResult.IdToken",
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
                  "idToken": "$root.AuthenticationResult.IdToken",
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
