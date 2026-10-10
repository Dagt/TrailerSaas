import type { RouteObject } from 'react-router'
import { AdminLayout, AdminHome } from '../areas/tenant-admin/AdminLayout'
import { MasterList, MasterDetail, SettingsPage } from '../areas/tenant-admin/MasterDataPages'
import { TransportOperationsArea } from '../areas/transport-operations/TransportOperationsArea'
import { CustomerPortalArea } from '../areas/customer-portal/CustomerPortalArea'
import { AppShell } from './AppShell'
import { Home } from './Home'
import { NotFound } from './NotFound'
import { LoadingState } from '../shared/ui/components'

export const appRoutes: RouteObject[] = [
  {
    Component: AppShell,
    HydrateFallback: LoadingState,
    children: [
      { index: true, Component: Home },
      { path: 'admin', Component: AdminLayout, children: [
        { index: true, Component: AdminHome },
        { path: 'customers', element: <MasterList kind="customers" /> },
        { path: 'customers/:customerId', element: <MasterDetail kind="customers" /> },
        { path: 'locations', element: <MasterList kind="sites" /> },
        { path: 'sites/:siteId', element: <MasterDetail kind="sites" /> },
        { path: 'drivers', element: <MasterList kind="drivers" /> },
        { path: 'drivers/:driverId', element: <MasterDetail kind="drivers" /> },
        { path: 'tractors', element: <MasterList kind="tractors" /> },
        { path: 'tractors/:tractorId', element: <MasterDetail kind="tractors" /> },
        { path: 'trailers', element: <MasterList kind="trailers" /> },
        { path: 'trailers/:trailerId', element: <MasterDetail kind="trailers" /> },
        { path: 'settings', Component: SettingsPage },
      ] },
      { path: 'operations', Component: TransportOperationsArea, children: [
        { index: true, lazy: async () => ({ Component: (await import('../areas/transport-operations/BoardPages')).Dashboard }) },
        { path: 'orders', lazy: async () => ({ Component: (await import('../areas/transport-operations/BoardPages')).OrdersQueue }) },
        { path: 'orders/:orderId', lazy: async () => ({ Component: (await import('../areas/transport-operations/TrailerPages')).OperationalOrderDetail }) },
        { path: 'trailers', lazy: async () => ({ Component: (await import('../areas/transport-operations/TrailerPages')).TrailerSearchPage }) },
        { path: 'trailers/:trailerId', lazy: async () => ({ Component: (await import('../areas/transport-operations/TrailerPages')).OperationalTrailerDetail }) },
        { path: 'trips', lazy: async () => ({ Component: (await import('../areas/transport-operations/BoardPages')).TripsList }) },
        { path: 'trips/new', lazy: async () => ({ Component: (await import('../areas/transport-operations/PlanTrip')).PlanTrip }) },
        { path: 'trips/:tripId', lazy: async () => ({ Component: (await import('../areas/transport-operations/TripDetailPage')).TripDetailPage }) },
      ] },
      { path: 'customer', Component: CustomerPortalArea },
      { path: '*', Component: NotFound },
    ],
  },
]
