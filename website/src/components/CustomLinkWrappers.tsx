import React from 'react'
import { AnalyticsClient } from './Analytics/analytics-client'
import CustomLink from './CustomLink'

type CLStepByStepProps = {
    children: React.ReactNode
}
  
export const CLStepByStep: React.FC = ({ children }: CLStepByStepProps) => {
  const { text, href } = extractLinkInfo(children)
  const onClick = () => AnalyticsClient.startAndTrackEvent('GYHPPIBS')

  return (<CustomLink href={href} onClick={onClick}>{text}</CustomLink>)
}

export const CLExampleApps: React.FC = ({ children }: CLStepByStepProps) => {
  return createCustomLinkComponent(children, 'YY7T3ZSZ')
}

export const CLAskMeRepo: React.FC = ({ children }: CLStepByStepProps) => {
  return createCustomLinkComponent(children, 'NE1EADCK')
}

export const CLInstallBooster: React.FC = ({ children }: CLStepByStepProps) => {
  return createCustomLinkComponent(children, 'AXTW7ICE')
}

function createCustomLinkComponent(element: React.ReactNode, event: string) {
  const { text, href } = extractLinkInfo(element)
  const onClick = () => AnalyticsClient.startAndTrackEvent(event)
  return <CustomLink href={href} onClick={onClick}>{text}</CustomLink>
}

function extractLinkInfo(element: React.ReactNode) {
  if (React.isValidElement(element) && element.props.href) {
    return { text: element.props.children, href: element.props.href };
  }
  return { text: '', href: '' };
}