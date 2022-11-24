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
  const { copyright, links, style } = footer
  return (
    <>
      <footer className="w-full max-w-3xl mx-auto flex flex-col gap-32 py-16">
        <FooterLinkSection links={links as FooterLinkColumn[]} />
        <FooterCopyright copyright={copyright} />
      </footer>
    </>
  )
}
export default React.memo(Footer)
