package com.trailersaas.notifications.internal;

import com.trailersaas.notifications.AlertRecipientPolicy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Compiled test-only approved policy/provider, never a production default. */
@TestConfiguration(proxyBeanMethods=false)
public class DeliveryTestConfiguration {
 @Bean @Primary public Probe deliveryProbe(){return new Probe();}
 @Bean AlertRecipientPolicy testPolicy(Probe p){return new AlertRecipientPolicy(){public String code(){return "TEST_APPROVED";}public Set<UUID> recipients(Context c){return Set.copyOf(p.recipients);}};}
 public static class Probe implements FcmTransport {
  public final Set<UUID> recipients=java.util.concurrent.ConcurrentHashMap.newKeySet();
  public final AtomicInteger calls=new AtomicInteger();
  public volatile String scenario="ACCEPTED_BY_FCM";
  public volatile Map<String,String> references=Map.of();
  public volatile Runnable duringSend=()->{};
  public void reset(UUID user){recipients.clear();recipients.add(user);calls.set(0);scenario="ACCEPTED_BY_FCM";references=Map.of();duringSend=()->{};}
  public List<Result> send(List<String> tokens,Map<String,String> references){
   int call=calls.incrementAndGet();this.references=Map.copyOf(references);duringSend.run();
   if(scenario.equals("CRASH_ONCE")&&call==1)throw new IllegalStateException("test-only-external-effect-then-process-loss");
   if(scenario.equals("MALFORMED"))return List.of();
   return tokens.stream().map(token->{
    String mode=scenario.equals("RETRY_ONCE")?(call==1?"RETRYABLE_FAILURE":"ACCEPTED_BY_FCM"):scenario.equals("MIXED")?(token.endsWith("bad")?"INVALIDATED_TOKEN":"ACCEPTED_BY_FCM"):scenario.equals("CRASH_ONCE")?"ACCEPTED_BY_FCM":scenario;
    if(scenario.equals("PERMANENT_AND_RETRY"))mode=token.endsWith("bad")?"PERMANENT_FAILURE":call==1?"RETRYABLE_FAILURE":"ACCEPTED_BY_FCM";
    var outcome=Outcome.valueOf(mode);return new Result(outcome,switch(outcome){case ACCEPTED_BY_FCM->"FCM_ACCEPTED";case RETRYABLE_FAILURE->"FCM_TRANSIENT";case PERMANENT_FAILURE->"FCM_PERMANENT";case INVALIDATED_TOKEN->"TOKEN_INVALID";});
   }).toList();
  }
 }
}
