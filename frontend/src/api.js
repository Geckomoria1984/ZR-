const runtimeConfig = typeof window === 'undefined' ? {} : window;

function defaultApiBase() {
  if (typeof window === 'undefined') return '';
  const { hostname, port } = window.location;
  const isLocalHost = hostname === 'localhost' || hostname === '127.0.0.1';
  const isStaticDevServer = ['5173', '5174', '5178'].includes(port);
  return isLocalHost && isStaticDevServer ? 'http://localhost:8080' : '';
}

export const API_BASE = runtimeConfig.__API_BASE__ ?? defaultApiBase();

export function apiUrl(path) {
  return `${API_BASE}${path}`;
}

export function normalizeApiPerson(person = {}) {
  if (!person.photoUrl || !String(person.photoUrl).startsWith('/api/')) return person;
  return {
    ...person,
    photoUrl: apiUrl(person.photoUrl),
  };
}
