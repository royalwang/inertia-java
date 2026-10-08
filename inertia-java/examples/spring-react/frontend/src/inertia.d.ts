import '@inertiajs/core'

declare module '@inertiajs/core' {
  interface InertiaConfig {
    // This example can run with first-message or all-errors server configuration.
    errorValueType: string | string[]
  }
}
