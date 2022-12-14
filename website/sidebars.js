// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'introduction',
    {
      type: 'category',
      label: 'Getting Started',
      link: {
        type: 'generated-index',
      },
      items: ['getting-started/installation', 'getting-started/configuration', 'getting-started/coding'],
    },
    {
      type: 'category',
      label: 'Booster architecture',
      collapsed: false,
      link: {
        type: 'doc',
        id: 'architecture/event-driven',
      },
      items: [
        'architecture/command',
        'architecture/event',
        'architecture/event-handler',
        'architecture/entity',
        'architecture/read-model',
      ],
    },
    {
      type: 'category',
      label: 'Features',
      collapsed: false,
      link: {
        type: 'generated-index',
      },
      items: [
        'features/event-stream',
        'features/schedule-actions',
        'features/logging',
        'features/error-handling',
        'features/data-migrations',
        'features/testing',
      ],
    },
    {
      type: 'category',
      label: 'Security',
      link: {
        type: 'doc',
        id: 'security/security',
      },
      items: ['security/authentication', 'security/authorization'],
    },
    'graphql',
    'booster-cli',
    'going-deeper',
    'frequently-asked-questions',
    'contributing',
  ],
}

module.exports = sidebars
