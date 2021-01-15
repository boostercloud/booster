import { CognitoUserPoolEvent } from 'aws-lambda'

export const handler = async (event: CognitoUserPoolEvent): Promise<CognitoUserPoolEvent> => {
  const expectedAnswer = event.request.privateChallengeParameters?.secretLoginCode ?? ''
  if (event.request.challengeAnswer === expectedAnswer) {
    event.response.answerCorrect = true
  } else {
    event.response.answerCorrect = false
  }
  return event
}
