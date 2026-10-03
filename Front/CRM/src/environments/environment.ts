/**
 * Development environment.
 *
 * ng serve runs on port 4200 while the API runs elsewhere, so the URLs are
 * absolute. This file is replaced by environment.prod.ts in a production build,
 * see the fileReplacements entry in angular.json.
 *
 * To go through the API gateway on a single origin during development, replace
 * these with a relative path and add the proxy config to the serve target:
 *   ng serve --proxy-config proxy.conf.json
 */
export const environment = {
  production: false,

  /**
   * Base URL of the API gateway, which fronts the auth, student, task and
   * notification services.
   */
  apiBaseUrl: 'http://localhost:8060',

  /**
   * Base URL of the task service. The gateway exposes it on /tasks, and nginx
   * proxies that path, so this stays separate from apiBaseUrl.
   */
  taskBaseUrl: 'http://localhost:8084',

  /**
   * Base URL of the event service. The gateway exposes it on /api/events,
   * /api/venues, /api/event-types and /api/contacts, and nginx proxies /api,
   * so the same origin as the gateway is enough.
   */
  eventBaseUrl: 'http://localhost:8089'
};
