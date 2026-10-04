import type { RouteObject } from 'react-router'
import { TenantAdminArea } from '../areas/tenant-admin/TenantAdminArea'
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
      { path: 'admin', Component: TenantAdminArea },
      { path: 'operations', Component: TransportOperationsArea },
      { path: 'customer', Component: CustomerPortalArea },
      { path: '*', Component: NotFound },
    ],
  },
]
