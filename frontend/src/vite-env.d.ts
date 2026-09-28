/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin for production builds; empty in dev (Vite proxies instead). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
