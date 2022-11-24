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
    <div className="flex justify-around">
      {links.map(({ title, items }) => (
        <div key={title} className="flex flex-col gap-6">
          <h5 className="uppercase text-black underline">{title}</h5>
          <ul className="flex flex-col gap-4">
            {items.map(({ to, label }) => (
              <li key={to}>
                <a href={to}>{label}</a>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}

export default FooterLinkSection
