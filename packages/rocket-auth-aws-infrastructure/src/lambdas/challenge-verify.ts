import { CognitoUserPoolEvent } from 'aws-lambda'

export const handler = async (event: CognitoUserPoolEvent): Promise<CognitoUserPoolEvent> => {
  const expectedAnswer = event.request.privateChallengeParameters?.secretLoginCode ?? ''
  event.response.answerCorrect = event.request.challengeAnswer === expectedAnswer
  return event
}
