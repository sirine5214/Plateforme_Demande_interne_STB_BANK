export interface NavigationItem {
  id: string;
  title: string;
  type: 'item' | 'collapse' | 'group';
  translate?: string;
  icon?: string;
  hidden?: boolean;
  url?: string;
  classes?: string;
  exactMatch?: boolean;
  external?: boolean;
  /** Paramètres de requête transmis au routerLink (ex. filtre de statut). */
  queryParams?: Record<string, string>;
  target?: boolean;
  breadcrumbs?: boolean;
  badge?: {
    title?: string;
    type?: string;
  };
  children?: NavigationItem[];
}

/**
 * Menu Administrateur — BF 2.1 : gestion des utilisateurs, des rôles et des permissions.
 */
export const AdminNavigationItems: NavigationItem[] = [
  {
    id: 'administration',
    title: 'Administration',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'admin-dashboard',
        title: 'Tableau de bord',
        type: 'item',
        url: '/admin/dashboard',
        icon: 'feather icon-home'
      },
      {
        id: 'admin-users',
        title: 'Gestion des utilisateurs',
        type: 'item',
        url: '/admin/users',
        icon: 'feather icon-users'
      }
    ]
  },
  {
    id: 'admin-supervision',
    title: 'Supervision',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'admin-demandes',
        title: 'Toutes les demandes',
        type: 'item',
        url: '/demandes',
        icon: 'feather icon-list'
      },
      {
        id: 'admin-statistiques',
        title: 'Statistiques globales',
        type: 'item',
        url: '/statistiques',
        icon: 'feather icon-pie-chart'
      }
    ]
  },
  {
    id: 'admin-compte',
    title: 'Mon compte',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'admin-profil',
        title: 'Mon profil',
        type: 'item',
        url: '/profile',
        icon: 'feather icon-user'
      }
    ]
  }
];

/**
 * Menu Demandeur — BF 2.2.1 (création), 2.2.7 (consultation), 2.3.3 (suivi).
 */
export const DemandeurNavigationItems: NavigationItem[] = [
  {
    id: 'espace-demandeur',
    title: 'Mes demandes',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'tableau-bord-demandeur',
        title: 'Tableau de bord',
        type: 'item',
        url: '/statistiques',
        icon: 'feather icon-home'
      },
      {
        id: 'nouvelle-demande',
        title: 'Nouvelle demande',
        type: 'item',
        url: '/demandes',
        queryParams: { nouvelle: '1' },
        icon: 'feather icon-plus-circle'
      },
      {
        id: 'mes-demandes',
        title: 'Toutes mes demandes',
        type: 'item',
        url: '/demandes',
        icon: 'feather icon-file-text'
      },
      {
        id: 'demandes-en-cours',
        title: 'En cours de traitement',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'EN_COURS' },
        icon: 'feather icon-clock'
      },
      {
        id: 'demandes-terminees',
        title: 'Demandes clôturées',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'TERMINEE' },
        icon: 'feather icon-check-circle'
      }
    ]
  },
  {
    id: 'compte-demandeur',
    title: 'Mon compte',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'profil-demandeur',
        title: 'Mon profil',
        type: 'item',
        url: '/profile',
        icon: 'feather icon-user'
      }
    ]
  }
];

/**
 * Menu Développeur — BF 2.2.7 (consultation filtrée), 2.3.1 (mise à jour des statuts).
 */
export const DeveloppeurNavigationItems: NavigationItem[] = [
  {
    id: 'espace-developpeur',
    title: 'Mes tâches',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'tableau-bord-developpeur',
        title: 'Mon activité',
        type: 'item',
        url: '/statistiques',
        icon: 'feather icon-home'
      },
      {
        id: 'mes-taches',
        title: 'Toutes mes tâches',
        type: 'item',
        url: '/demandes',
        icon: 'feather icon-check-square'
      },
      {
        id: 'taches-en-cours',
        title: 'En cours',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'EN_COURS' },
        icon: 'feather icon-clock'
      },
      {
        id: 'taches-en-validation',
        title: 'En attente de validation',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'EN_VALIDATION' },
        icon: 'feather icon-send'
      }
    ]
  },
  {
    id: 'compte-developpeur',
    title: 'Mon compte',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'profil-developpeur',
        title: 'Mon profil',
        type: 'item',
        url: '/profile',
        icon: 'feather icon-user'
      }
    ]
  }
];

/**
 * Menu Chef de projet — BF 2.2.5 (affectation), 2.3 (validation du workflow), 2.4 (statistiques).
 */
export const ChefProjetNavigationItems: NavigationItem[] = [
  {
    id: 'pilotage',
    title: 'Pilotage',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'statistiques',
        title: 'Tableau de bord',
        type: 'item',
        url: '/statistiques',
        icon: 'feather icon-pie-chart'
      },
      {
        id: 'suivi-demandes',
        title: 'Suivi des demandes',
        type: 'item',
        url: '/demandes',
        icon: 'feather icon-list'
      }
    ]
  },
  {
    id: 'traitement',
    title: 'Traitement',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'demandes-a-affecter',
        title: 'À affecter',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'NOUVELLE' },
        icon: 'feather icon-inbox'
      },
      {
        id: 'demandes-a-valider',
        title: 'À valider',
        type: 'item',
        url: '/demandes',
        queryParams: { statut: 'EN_VALIDATION' },
        icon: 'feather icon-check-circle'
      }
    ]
  },
  {
    id: 'compte-chef',
    title: 'Mon compte',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'profil-chef',
        title: 'Mon profil',
        type: 'item',
        url: '/profile',
        icon: 'feather icon-user'
      }
    ]
  }
];

export const NavigationItems: NavigationItem[] = [
  {
    id: 'navigation',
    title: 'Navigation',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'dashboard',
        title: 'Dashboard',
        type: 'item',
        url: '/analytics',
        icon: 'feather icon-home'
      }
    ]
  },
  {
    id: 'ui-component',
    title: 'Ui Component',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'basic',
        title: 'Component',
        type: 'collapse',
        icon: 'feather icon-box',
        children: [
          {
            id: 'button',
            title: 'Button',
            type: 'item',
            url: '/component/button'
          },
          {
            id: 'badges',
            title: 'Badges',
            type: 'item',
            url: '/component/badges'
          },
          {
            id: 'breadcrumb-pagination',
            title: 'Breadcrumb & Pagination',
            type: 'item',
            url: '/component/breadcrumb-paging'
          },
          {
            id: 'collapse',
            title: 'Collapse',
            type: 'item',
            url: '/component/collapse'
          },
          {
            id: 'tabs-pills',
            title: 'Tabs & Pills',
            type: 'item',
            url: '/component/tabs-pills'
          },
          {
            id: 'typography',
            title: 'Typography',
            type: 'item',
            url: '/component/typography'
          }
        ]
      }
    ]
  },
  {
    id: 'Authentication',
    title: 'Authentication',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'signup',
        title: 'Sign up',
        type: 'item',
        url: '/register',
        icon: 'feather icon-at-sign',
        target: true,
        breadcrumbs: false
      },
      {
        id: 'signin',
        title: 'Sign in',
        type: 'item',
        url: '/login',
        icon: 'feather icon-log-in',
        target: true,
        breadcrumbs: false
      }
    ]
  },
  {
    id: 'chart',
    title: 'Chart',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'apexchart',
        title: 'ApexChart',
        type: 'item',
        url: '/chart',
        classes: 'nav-item',
        icon: 'feather icon-pie-chart'
      }
    ]
  },
  {
    id: 'forms & tables',
    title: 'Forms & Tables',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'forms',
        title: 'Basic Forms',
        type: 'item',
        url: '/forms',
        classes: 'nav-item',
        icon: 'feather icon-file-text'
      },
      {
        id: 'tables',
        title: 'Tables',
        type: 'item',
        url: '/tables',
        classes: 'nav-item',
        icon: 'feather icon-server'
      }
    ]
  },
  {
    id: 'other',
    title: 'Other',
    type: 'group',
    icon: 'icon-group',
    children: [
      {
        id: 'sample-page',
        title: 'Sample Page',
        type: 'item',
        url: '/sample-page',
        classes: 'nav-item',
        icon: 'feather icon-sidebar'
      },
      {
        id: 'menu-level',
        title: 'Menu Levels',
        type: 'collapse',
        icon: 'feather icon-menu',
        children: [
          {
            id: 'menu-level-2.1',
            title: 'Menu Level 2.1',
            type: 'item',
            url: 'javascript:void(0)',
            external: true
          },
          {
            id: 'menu-level-2.2',
            title: 'Menu Level 2.2',
            type: 'collapse',
            children: [
              {
                id: 'menu-level-2.2.1',
                title: 'Menu Level 2.2.1',
                type: 'item',
                url: 'javascript:void(0)',
                external: true
              },
              {
                id: 'menu-level-2.2.2',
                title: 'Menu Level 2.2.2',
                type: 'item',
                url: 'javascript:void(0)',
                external: true
              }
            ]
          }
        ]
      }
    ]
  }
];
