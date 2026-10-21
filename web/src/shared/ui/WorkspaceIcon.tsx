/** Decorative navigation cues only; callers still own links and authorization. */
export function WorkspaceIcon({ to }: { to: string }) {
  const section = to.split('/').filter(Boolean).at(-1)
  const paths: Record<string, string> = {
    admin: 'M3 10 10 4l7 6v7H4v-7m4 7v-5h4v5',
    operations: 'M3 4h14v12H3zM3 8h14M8 8v8',
    customer: 'M3 4h6v5H3zM12 4h5v5h-5zM3 12h6v5H3zM12 12h5v5h-5z',
    customers: 'M7 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6M2 17v-2a5 5 0 0 1 10 0v2M13 4a3 3 0 0 1 0 6m2 2a4 4 0 0 1 3 4v1',
    locations: 'M15 8c0 4-5 9-5 9S5 12 5 8a5 5 0 1 1 10 0ZM8 8a2 2 0 1 0 4 0 2 2 0 0 0-4 0',
    drivers: 'M6 7a4 4 0 1 0 8 0 4 4 0 0 0-8 0M3 18v-2a7 7 0 0 1 14 0v2',
    tractors: 'M3 5h8v9H3zM11 9h4l3 3v3h-7M5 14a2 2 0 1 0 0 4 2 2 0 0 0 0-4M14 14a2 2 0 1 0 0 4 2 2 0 0 0 0-4',
    trailers: 'M2 5h16v9H2zM5 14v3m10-3v3M2 17h5m6 0h5',
    settings: 'M3 5h14M3 10h14M3 15h14M7 3v4m6 1v4m-6 1v4',
    alerts: 'm10 3 8 14H2L10 3Zm0 5v4m0 2v1',
    audit: 'M5 3h10v14H5zM8 7h4M8 10h4M8 13h3M3 6v11h10',
    orders: 'M5 3h10v14H5zM8 7h4M8 10h4M8 13h4',
    trips: 'M4 5a2 2 0 1 0 4 0 2 2 0 0 0-4 0m8 10a2 2 0 1 0 4 0 2 2 0 0 0-4 0M8 5h5a3 3 0 0 1 0 6H7a2 2 0 0 0 0 4h5',
    deliveries: 'M3 7 10 3l7 4v9l-7 3-7-3V7Zm0 0 7 4 7-4m-7 4v8M6 5l7 4',
    documents: 'M5 2h7l4 4v12H5V2Zm7 0v5h4M8 11h5m-5 3h5',
  }
  return <svg viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false"><path d={paths[section ?? ''] ?? paths.customer} stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
}
