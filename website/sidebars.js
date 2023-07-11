// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */

const sidebars = {
  docs: [
    'ai-assistant',
    'introduction',
    {
      type: 'category',
      label: 'Getting Started',
      link: {
        type: 'generated-index',
      },
      items: [
        {
          type: 'doc',
          id: 'getting-started/installation',
          label: 'Installation',
          customProps: {
            trackingEvent: 'HRYKQRPN',
          }
        },
        'getting-started/coding'
      ],
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
        'architecture/notifications',
        'architecture/queries',
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
        type: 'generated-index'
      },
      items: [
        'going-deeper/environment-configuration',
        {
          type: 'doc',
          label: 'The Register object',
          id: 'going-deeper/register',
        },
        'going-deeper/infrastructure-providers',
        'going-deeper/custom-providers',
        {
          type: 'category',
          label: 'Extending Booster with Rockets!',
          link: {
            type: 'doc',
            id: 'going-deeper/rockets'
          },
          items: [
            'going-deeper/rockets/rocket-file-uploads',
            'going-deeper/rockets/rocket-backup-booster',
            'going-deeper/rockets/rocket-static-sites',
            'going-deeper/rockets/rocket-webhook',
          ]
        },
        {
          type: 'category',
          label: 'Sensors',
          link: {
            type: 'doc',
            id: 'going-deeper/sensor'
          },
          items: [
            'going-deeper/health/sensor-health',
          ]
        },
        'going-deeper/testing',
        'going-deeper/data-migrations',
        'going-deeper/touch-entities',
        'going-deeper/custom-templates',
        'going-deeper/framework-packages',
        'going-deeper/instrumentation',
      ],
    },
    'frequently-asked-questions',
    'contributing',
  ],
}

module.exports = sidebars
