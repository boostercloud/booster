export interface CognitoAuthTemplate {
  response: string
  request: string
}

export const signUpTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
    {
      "ClientId": "${clientId}",
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
  }
}

export const signInTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
            {
              "ClientId": "${clientId}",
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
  }
}

export const refreshTokenTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
            {
              "ClientId":"${clientId}",
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
  }
}

export const confirmSignUpTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
    {
      "ClientId": "${clientId}",
      "ConfirmationCode": "$root.confirmationCode",
      "Username": "$root.username"
    }`,
    response: '{}',
  }
}

export const signOutTemplate = (): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
              {
                "AccessToken":"$root.accessToken" 
              }`,
    response: '{}',
  }
}

export const forgotPasswordTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
    {
      "ClientId": "${clientId}",
      "Username": "$root.username"
    }`,
    response: '{}',
  }
}

export const confirmForgotPasswordTemplate = (clientId: string): CognitoAuthTemplate => {
  return {
    request: `#set($root = $input.path('$'))
    {
      "ClientId": "${clientId}",
      "Username": "$root.username",
      "ConfirmationCode": "$root.confirmationCode",
      "Password": "$root.newPassword"
    }`,
    response: '{}',
  }
}
