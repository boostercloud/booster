import { CognitoUserPoolEvent } from 'aws-lambda'
import { SNS } from 'aws-sdk'

// It will create a 6 digit code to answer the challenge during the OTP flow
const randomSecretCode = (min = 100000, max = 999999): string => {
  return Math.floor(Math.random() * (max - min + 1) + min).toString()
}

export const handler = async (event: CognitoUserPoolEvent): Promise<CognitoUserPoolEvent> => {
  let secretLoginCode: string
  const sns = new SNS()
  if (!event.request.session || !event.request.session.length) {
    secretLoginCode = randomSecretCode()
    await sns
      .publish({
        Message: `Welcome, your confirmation code is: ${secretLoginCode}`,
        PhoneNumber: event.request.userAttributes.phone_number,
      })
      .promise()
  } else {
    const previousChallenge = event.request.session.slice(-1)[0]
    secretLoginCode = previousChallenge.challengeMetadata!.match(/CODE-(\d*)/)![1]
  }

  event.response.privateChallengeParameters = { secretLoginCode }

  event.response.challengeMetadata = `CODE-${secretLoginCode}`

  return event
}
