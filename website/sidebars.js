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
      items: ['getting-started/installation', 'getting-started/coding'],
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
        'features/error-handling'
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
    {
      type: 'category',
      label: 'Going deeper with Booster',
      link: {
        type: 'generated-index',
      },
      items: [
        'going-deep/environment-configuration',
        'going-deep/infrastructure-providers',
        'going-deep/framework-packages',
        'going-deep/testing',
        'going-deep/data-migrations',
        'going-deep/custom-providers',
        {
          type: 'category',
          label: 'Extending Booster with Rockets!',
          link: {
            type: 'doc',
            id: 'going-deep/rockets/rockets',
          },
          items: [
            'going-deep/rockets/rocket-authentication',
            'going-deep/rockets/rocket-file-uploads',
            'going-deep/rockets/rocket-backup-booster',
            'going-deep/rockets/rocket-static-sites',
            'going-deep/rockets/rocket-webhook',
          ]
        },
        'going-deep/custom-templates'
      ],
    },
    'frequently-asked-questions',
    'contributing',
  ],
}

module.exports = sidebars
