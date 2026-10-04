/**
 * Production environment.
 *
 * The application is served by nginx, which proxies /api to the API gateway and
 * /tasks to the task service. The base URLs are therefore empty and every call
 * stays on the origin the browser already reached, which makes the bundle work
 * behind any hostname without being rebuilt:
 *
 *   '' + '/api/v1/auth'  ->  /api/v1/auth   ->  nginx -> gateway -> auth-service
 *
 * Hardcoding an absolute URL such as http://localhost:8060 here would make the
 * browser call its own machine instead of the server hosting the page, so the
 * whole application would appear broken to anyone but the developer.
 */
export const environment = {
  production: true,

  /** Served by nginx, proxied to the API gateway on port 8060. */
  apiBaseUrl: '',

  /** Served by nginx, proxied to the API gateway which routes /tasks. */
  taskBaseUrl: '',

  /**
   * Served by nginx through /api. The gateway routes /api/events,
   * /api/venues, /api/event-types and /api/contacts to the event service.
   */
  eventBaseUrl: ''
};
