import type { RouteObject } from 'react-router'
import { AdminLayout, AdminHome } from '../areas/tenant-admin/AdminLayout'
import { MasterList, MasterDetail, SettingsPage } from '../areas/tenant-admin/MasterDataPages'
import { TransportOperationsArea } from '../areas/transport-operations/TransportOperationsArea'
import { CustomerDashboard, CustomerSection } from '../areas/customer-portal/CustomerPages'
import { CustomerPortalArea } from '../areas/customer-portal/CustomerPortalArea'
import { OrdersList } from '../areas/customer-portal/OrdersList'
import { CreateOrder } from '../areas/customer-portal/CreateOrder'
import { CustomerOrderDetail } from '../areas/customer-portal/OrderDetail'
import { AppShell } from './AppShell'
import { Home } from './Home'
import { NotFound } from './NotFound'
import { LoadingState } from '../shared/ui/components'
import { AdminAlerts, OperationsAlerts } from '../areas/alerts/AlertsWorkspace'

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
        { path: 'alerts', Component: AdminAlerts },
        { path: 'alerts/:alertId', Component: AdminAlerts },
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
        { path: 'alerts', Component: OperationsAlerts },
        { path: 'alerts/:alertId', Component: OperationsAlerts },
      ] },
      { path: 'customer', Component: CustomerPortalArea, children: [
        { index: true, Component: CustomerDashboard },
        { path:'orders',Component:OrdersList },
        { path:'orders/new',Component:CreateOrder },
        { path:'orders/:orderId',Component:CustomerOrderDetail },
        ...['trailers','deliveries','documents'].map(section => ({ path: section, element: <CustomerSection section={section} /> })),
        { path: '*', Component: NotFound },
      ] },
      { path: '*', Component: NotFound },
    ],
  },
]
