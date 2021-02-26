/* eslint-disable @typescript-eslint/indent */
import { CognitoUserPoolEvent } from 'aws-lambda'

type Session = Array<{
  challengeName:
    | 'CUSTOM_CHALLENGE'
    | 'PASSWORD_VERIFIER'
    | 'SMS_MFA'
    | 'DEVICE_SRP_AUTH'
    | 'DEVICE_PASSWORD_VERIFIER'
    | 'ADMIN_NO_SRP_AUTH'
  challengeResult: boolean
  challengeMetadata?: string
}>

// User answer helpers
const onlyAcceptingCustomChallenges = (session: Session): boolean => {
  return session.find((attempt) => attempt.challengeName !== 'CUSTOM_CHALLENGE') !== undefined
}

const userProvidedRightAnswer = (session: Session): boolean => {
  return (
    session.length > 0 &&
    session.slice(-1)[0].challengeName === 'CUSTOM_CHALLENGE' &&
    session.slice(-1)[0].challengeResult === true
  )
}

const userProvidedWrongAnswerThreeTimes = (session: Session): boolean => {
  return session.length >= 3 && session.slice(-1)[0].challengeResult === false
}

// Response helpers
const presentChallenge = (event: CognitoUserPoolEvent): CognitoUserPoolEvent => {
  event.response.issueTokens = false
  event.response.failAuthentication = false
  event.response.challengeName = 'CUSTOM_CHALLENGE'
  return event
}

const succeedAuth = (event: CognitoUserPoolEvent): CognitoUserPoolEvent => {
  event.response.issueTokens = true
  event.response.failAuthentication = false
  return event
}

const failAuth = (event: CognitoUserPoolEvent): CognitoUserPoolEvent => {
  event.response.issueTokens = false
  event.response.failAuthentication = true
  return event
}

export const handler = async (event: CognitoUserPoolEvent): Promise<CognitoUserPoolEvent> => {
  if (!event.request.session) {
    return event
  }
  const currentSession = event.request.session

  if (onlyAcceptingCustomChallenges(currentSession) || userProvidedWrongAnswerThreeTimes(currentSession)) {
    return failAuth(event)
  } else if (userProvidedRightAnswer(currentSession)) {
    return succeedAuth(event)
  } else {
    return presentChallenge(event)
  }
}
