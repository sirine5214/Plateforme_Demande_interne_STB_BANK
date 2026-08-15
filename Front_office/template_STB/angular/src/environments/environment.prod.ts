import packageInfo from '../../package.json';

// En production, le frontend est servi par Nginx (voir Dockerfile / nginx.conf.template),
// qui proxifie /api, /uploads et /ws vers le Back_office. On reste donc sur l'origine
// courante plutôt que de coder en dur "localhost:8080", qui n'existe ni en Docker ni en k8s.
const origin = typeof window !== 'undefined' ? window.location.origin : '';

export const environment = {
  appVersion: packageInfo.version,
  production: true,
  apiUrl: `${origin}/api`,
  apiOrigin: origin
};
