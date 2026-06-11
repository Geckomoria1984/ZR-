const runtimeConfig = typeof window === 'undefined' ? {} : window;

export const API_BASE = runtimeConfig.__API_BASE__ || 'http://localhost:8080';

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
