import React from 'react'
import { AnalyticsClient } from './Analytics/analytics-client'
import CustomLink from './CustomLink'

type CLStepByStepProps = {
    children: React.ReactNode
}
  
export const CLStepByStep: React.FC = ({ children }: CLStepByStepProps) => {
  const href = 'https://github.com/boostercloud/booster/tree/main/examples'
  const onClick = () => AnalyticsClient.startAndTrackEvent('GYHPPIBS')

  return (<CustomLink href={href} onClick={onClick}>{children}</CustomLink>)
}

export const CLExampleApps: React.FC = ({ children }: CLStepByStepProps) => {
  const href = 'https://github.com/boostercloud/examples'
  const onClick = () => AnalyticsClient.startAndTrackEvent('YY7T3ZSZ')

  return (<CustomLink href={href} onClick={onClick}>{children}</CustomLink>)
}

export const CLAskMeRepo: React.FC = ({ children }: CLStepByStepProps) => {
  const href = 'https://github.com/boostercloud/examples/tree/master/askme'
  const onClick = () => AnalyticsClient.startAndTrackEvent('NE1EADCK')

  return (<CustomLink href={href} onClick={onClick}>{children}</CustomLink>)
}

export const CLInstallBooster: React.FC = ({ children }: CLStepByStepProps) => {
  const href = 'https://www.npmjs.com/package/@boostercloud/cli'
  const onClick = () => AnalyticsClient.startAndTrackEvent('AXTW7ICE')

  return (<CustomLink href={href} onClick={onClick}>{children}</CustomLink>)
}