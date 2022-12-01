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
    <div className="footer-cr">
      <p>
        Booster is an open-source initiative from <a href="https://www.theagilemonkeys.com/">The Agile Monkeys.</a>
      </p>
      <p>
        <a href="https://www.boosterframework.com/privacy-policy">Privacy Policy</a>
      </p>
    </div>
  )
}

export default FooterCopyright
