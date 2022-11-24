import React, { FC } from 'react'

export interface FooterLinkColumn {
  title: string
  items: FooterLinkItem[]
}

interface FooterLinkItem {
  label: string
  to: string
}

interface FooterCopyrightProps {
  copyright: string
}

export const FooterCopyright: FC<FooterCopyrightProps> = ({ copyright = '' }) => {
  return (
    <div className="flex flex-col justify-center items-center gap-20">
      <p className="text-center">
        Booster is an open-source initiative from{' '}
        <a href="https://www.theagilemonkeys.com/" className="font-bold">
          The Agile Monkeys.
        </a>
      </p>
      <p>
        <a href="https://www.boosterframework.com/privacy-policy">Privacy Policy</a>
      </p>
    </div>
  )
}

export default FooterCopyright
