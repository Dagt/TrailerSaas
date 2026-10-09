import type { RouteObject } from 'react-router'
import { AdminLayout, AdminHome } from '../areas/tenant-admin/AdminLayout'
import { MasterList, MasterDetail, SettingsPage } from '../areas/tenant-admin/MasterDataPages'
import { TransportOperationsArea } from '../areas/transport-operations/TransportOperationsArea'
import { CustomerPortalArea } from '../areas/customer-portal/CustomerPortalArea'
import { AppShell } from './AppShell'
import { Home } from './Home'
import { NotFound } from './NotFound'

export const appRoutes: RouteObject[] = [
  {
    Component: AppShell,
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
      { path: 'operations', Component: TransportOperationsArea },
      { path: 'customer', Component: CustomerPortalArea },
      { path: '*', Component: NotFound },
    ],
  },
]
