import { useThemeConfig } from '@docusaurus/theme-common'
import React from 'react'
import FooterCopyright from './FooterCopyright'
import FooterLinkSection from './FooterLinkSection'

interface FooterLinkColumn {
  title: string
  items: FooterLinkItem[]
}

interface FooterLinkItem {
  label: string
  to: string
}

function Footer() {
  const { footer } = useThemeConfig()
  if (!footer) {
    return null
  }
  const { copyright, links } = footer
  return (
    <>
      <footer className="footer-container">
        <FooterLinkSection links={links as FooterLinkColumn[]} />
        <FooterCopyright copyright={copyright} />
      </footer>
    </>
  )
}
export default React.memo(Footer)
