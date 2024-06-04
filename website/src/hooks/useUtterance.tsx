import { useEffect, useRef } from 'react'

export type Params = {
  repo: string
  theme:
    | 'github-light'
    | 'github-dark'
    | 'preferred-color-scheme'
    | 'github-dark-orange'
    | 'icy-dark'
    | 'dark-blue'
    | 'photon-dark'
    | 'boxy-light'
    | 'gruvbox-dark'
  label?: string
  'issue-term'?: 'pathname' | 'url' | 'title' | 'og:title'
  'issue-number'?: string
}

export default function useUtterance<T extends HTMLElement>(params?: Params) {
  const anchor = useRef<T>(null)

  useEffect(() => {
    while (anchor.current.firstChild) {
      anchor.current.removeChild(anchor.current.firstChild)
    }

    anchor.current.appendChild(createUtteranceScript(params))
  }, [params])

  return { anchor }
}

function createUtteranceScript(option?: Record<string, unknown>) {
  const script = document.createElement('script')

  script.src = 'https://utteranc.es/client.js'
  script.crossOrigin = 'anonymous'
  script.async = true

  Object.entries({ ...defaultAttributes, ...option }).forEach(([key, value]) => {
    script.setAttribute(key, value)
  })

  return script
}

const defaultAttributes = {
  'issue-term': 'title',
  label: 'comment-section',
  theme: 'github-light',
}
