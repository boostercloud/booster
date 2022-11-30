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
    'authentication',
    'logging',
    'graphql',
    'error-handling',
    'data-migrations',
    'testing',
    'frequently-asked-questions',
    'contributing',
  ],
}

module.exports = sidebars
