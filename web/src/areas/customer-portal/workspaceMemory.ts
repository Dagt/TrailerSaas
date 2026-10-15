import { createContext, useContext, useState } from 'react'

/** Ephemeral drafts only. A new map is allocated on owner/Tenant/Customer change. */
export const WorkspaceMemory = createContext<Map<string,unknown> | null>(null)
export function useWorkspaceRecord<T>(key:string, initial:()=>T, recover?:(stored:T)=>T) {
  const memory=useContext(WorkspaceMemory)
  if(!memory) throw new Error('Customer workspace memory is required.')
  const [value,setValue]=useState<T>(()=>{
    if(!memory.has(key))return initial()
    const value=memory.get(key) as T, next=recover?recover(value):value
    memory.set(key,next)
    return next
  })
  const save=(next:T)=>{memory.set(key,next);setValue(next)}
  return {value,save,memory,key}
}
