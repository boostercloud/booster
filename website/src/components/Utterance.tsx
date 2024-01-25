import React, { useMemo } from 'react'
// import { useColorMode } from "@docusaurus/theme-common";

import useUtterance from '../hooks/useUtterance'

export type Props = React.HTMLAttributes<HTMLDivElement>

export default function Utterance(props: Props) {
  // const { colorMode } = useColorMode();
  const colorMode = 'light'
  const options = useMemo(
    () =>
      ({
        repo: 'boostercloud/docs-discussion',
        theme: `github-${colorMode}`,
        label: 'comment-section',
      } as const),
    [colorMode]
  )

  const { anchor } = useUtterance<HTMLDivElement>(options)

  return <div ref={anchor} {...props} />
}
