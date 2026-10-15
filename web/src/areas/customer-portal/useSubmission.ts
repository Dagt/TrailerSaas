import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiProblem } from '../../shared/api/client'
import { customerKey } from './queries'
import { useCustomerContext } from './context'
import { useWorkspaceRecord } from './workspaceMemory'

type State = {status:'idle'|'pending'|'uncertain'|'blocked'; error?:unknown}
/** No mutation retry/optimism. Unmount is an uncertain result, never permission to repeat. */
export function useSubmission(key:string) {
  const record=useWorkspaceRecord<State>(`submission:${key}`,()=>({status:'idle'}),stored=>stored.status==='pending'?{status:'uncertain'}:stored)
  const [receipt,setReceipt]=useState(false), active=useRef(true), controller=useRef<AbortController|null>(null)
  const owner=useCustomerContext(), cache=useQueryClient()
  useEffect(()=>{active.current=true;return ()=>{
    active.current=false;controller.current?.abort()
    const current=record.memory.get(record.key) as State|undefined
    if(current?.status==='pending') record.memory.set(record.key,{status:'uncertain'})
  }},[record.memory,record.key])
  async function send<T>(command:(signal:AbortSignal)=>Promise<T>,accepted:(value:T)=>void) {
    if(record.value.status!=='idle' || controller.current) return
    const request=new AbortController();controller.current=request;record.save({status:'pending'})
    try {
      const data=await command(request.signal)
      if(!active.current || request.signal.aborted) return
      record.save({status:'blocked'});setReceipt(true)
      // Scope-owned invalidation only; callbacks cannot navigate or populate a new owner.
      const prefix=customerKey(owner,'orders').slice(0,5)
      void cache.invalidateQueries({predicate:q=>prefix.every((value,index)=>q.queryKey[index]===value)&&['orders','order','order-progress'].includes(String(q.queryKey[5]))})
      if(active.current&&!request.signal.aborted) accepted(data)
    } catch(error) {
      if(!active.current) return
      const problem=error instanceof ApiProblem?error.problem:undefined
      const uncertain=!problem || problem.status>=500 || problem.status===408
      record.save({status:uncertain?'uncertain':'blocked',error})
    } finally {controller.current=null}
  }
  const canRevise=record.value.error instanceof ApiProblem&&(record.value.error.problem.status===400||record.value.error.problem.status===409&&record.value.error.problem.code==='RESOURCE_CONFLICT')
  const revise=()=>{if(canRevise)record.save({status:'idle'})}
  return {...record.value,receipt,send,canRevise,revise,busy:record.value.status==='pending',blocked:record.value.status!=='idle'}
}
