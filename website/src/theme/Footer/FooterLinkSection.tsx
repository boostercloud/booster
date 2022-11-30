import React, { FC } from 'react'

export interface FooterLinkColumn {
  title: string
  items: FooterLinkItem[]
}

interface FooterLinkItem {
  label: string
  to: string
}

interface FooterLinkSectionProps {
  links: FooterLinkColumn[]
}

export const FooterLinkSection: FC<FooterLinkSectionProps> = ({ links = [] }) => {
  return (
    <div className="footer-ls">
      {links.map(({ title, items }) => (
        <div key={title} className="footer-ls-section">
          <h5 className="footer-ls-section-title">{title}</h5>
          <div className="footer-ls-column">
            {items.map(({ to, label }) => (
              <a href={to}>{label}</a>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

export default FooterLinkSection
