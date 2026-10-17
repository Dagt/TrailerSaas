import { useEffect, useId, useRef } from 'react'
import type { PropsWithChildren, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link, NavLink, useLocation } from 'react-router'
import { Button } from './components'

export interface WorkspaceLink { label: string; to: string; end?: boolean }

/** Presentation only. The portal supplies authorized links and owns all context. */
export function WorkspaceHeader({ title, identity, children, actions }: PropsWithChildren<{ title: string; identity?: string; actions: ReactNode }>) {
  return <header className="workspace-header">
    <div className="workspace-heading"><span className="workspace-eyebrow">Workspace</span><strong>{title}</strong>{identity && <span className="workspace-identity">{identity}</span>}</div>
    <div className="workspace-context">{children}</div>
    <div className="workspace-actions">{actions}</div>
  </header>
}

function NavigationLinks({ links, onNavigate }: { links: WorkspaceLink[]; onNavigate?(): void }) {
  return <>{links.map(link => <NavLink key={link.to} to={link.to} end={link.end} onClick={onNavigate}>
    <span className="navigation-mark" aria-hidden="true"><svg viewBox="0 0 20 20" fill="none"><rect x="3" y="3" width="5" height="5" rx="1" /><path d="M12 4h5M12 7h5M3 12h14M3 16h14" /></svg></span>{link.label}
  </NavLink>)}</>
}

export function WorkspaceNavigation({ label, links, context }: { label: string; links: WorkspaceLink[]; context: string }) {
  const id = useId(), dialog = useRef<HTMLDialogElement>(null), trigger = useRef<HTMLButtonElement>(null)
  const location = useLocation()
  const close = () => {
    const element = dialog.current
    if (!element?.open) return
    element.close()
    trigger.current?.focus()
  }
  useEffect(() => {
    // Back/Forward and direct in-app navigation must also dismiss the modal.
    const element = dialog.current
    if (element?.open) { element.close(); trigger.current?.focus() }
  }, [location.key])
  useEffect(() => {
    const element = dialog.current
    const media = window.matchMedia?.('(min-width: 53.126rem)')
    const resize = () => {
      if (media?.matches && dialog.current?.open) {
        dialog.current.close()
        // The mobile trigger is now hidden; focus the desktop active link.
        document.getElementById(id)?.querySelector<HTMLElement>('[aria-current="page"]')?.focus()
      }
    }
    media?.addEventListener('change', resize)
    return () => { media?.removeEventListener('change', resize); if (element?.open) element.close() }
  }, [id])
  return <>
    <aside className="workspace-sidebar" id={id}>
      <Link className="sidebar-home" to="/">TrailerSaaS <span aria-hidden="true">↗</span></Link>
      <p className="sidebar-context">Workspace: {context}</p>
      <nav aria-label={label}><NavigationLinks links={links} /></nav>
      <p className="sidebar-footnote">Your workspace, in focus.</p>
    </aside>
    <div className="workspace-mobile-bar"><button type="button" ref={trigger} aria-haspopup="dialog" aria-controls={`${id}-drawer`} onClick={() => dialog.current?.showModal()}>Open navigation</button><span>Workspace: {context}</span></div>
    {createPortal(<dialog className="workspace-drawer" ref={dialog} id={`${id}-drawer`} aria-labelledby={`${id}-title`}
      onCancel={event => { event.preventDefault(); close() }} onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); close() } }}
      onClick={event => { if (event.target === event.currentTarget) { const bounds = event.currentTarget.getBoundingClientRect(); if (event.clientX > bounds.right || event.clientX < bounds.left || event.clientY < bounds.top || event.clientY > bounds.bottom) close() } }}>
      <div className="drawer-heading"><strong id={`${id}-title`}>{label}</strong><Button onClick={close} autoFocus>Close navigation</Button></div>
      <p className="sidebar-context">Workspace: {context}</p>
      <nav aria-label={`Mobile ${label}`}><NavigationLinks links={links} onNavigate={close} /><Link to="/" onClick={close}>Return to home</Link></nav>
    </dialog>, document.body)}
  </>
}

export function WorkspaceState({ title, children }: PropsWithChildren<{ title: string }>) {
  return <section className="workspace-state"><p className="workspace-eyebrow">TrailerSaaS</p><h1>{title}</h1>{children}</section>
}

export function AuthLayout({ children }: PropsWithChildren) {
  return <div className="auth-layout"><div className="auth-intro"><span className="workspace-eyebrow">TrailerSaaS</span><h2>A clear view of your work.</h2><p>One place for your authorized logistics workspace.</p></div><section className="sign-in">{children}</section></div>
}
