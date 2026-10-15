package com.trailersaas.notifications.internal;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FirebaseFcmTransport implements FcmTransport {
 final ObjectProvider<FirebaseApp> firebase;final boolean enabled;
 FirebaseFcmTransport(ObjectProvider<FirebaseApp> firebase,@Value("${trailersaas.notifications.fcm.enabled:false}")boolean enabled){this.firebase=firebase;this.enabled=enabled;}
 public List<Result> send(List<String> tokens,Map<String,String> references){
  if(tokens.isEmpty()||tokens.size()>32||!references.keySet().equals(Set.of("kind","tenantId","alertId"))||!"ALERT".equals(references.get("kind")))throw new IllegalArgumentException("Invalid push envelope");
  UUID.fromString(references.get("tenantId"));UUID.fromString(references.get("alertId"));
  if(!enabled||firebase.getIfAvailable()==null)return Collections.nCopies(tokens.size(),new Result(Outcome.RETRYABLE_FAILURE,"TRANSPORT_DISABLED"));
  var message=MulticastMessage.builder().addAllTokens(tokens).putAllData(references)
   .setNotification(Notification.builder().setTitle("TrailerSaaS").setBody("An operational alert is available. Open the app to review.").build())
   .setAndroidConfig(AndroidConfig.builder().setTtl(300000).setCollapseKey(references.get("alertId")).build())
   .setApnsConfig(ApnsConfig.builder().putHeader("apns-collapse-id",references.get("alertId")).build()).build();
  var pending=FirebaseMessaging.getInstance(firebase.getObject()).sendEachForMulticastAsync(message);
  try{
   var response=pending.get(8,TimeUnit.SECONDS);if(response.getResponses().size()!=tokens.size())throw new IllegalStateException("Invalid FCM receipt count");
   return response.getResponses().stream().map(r->r.isSuccessful()?new Result(Outcome.ACCEPTED_BY_FCM,"FCM_ACCEPTED"):classify(r.getException()==null?null:r.getException().getMessagingErrorCode())).toList();
  }catch(InterruptedException e){Thread.currentThread().interrupt();pending.cancel(true);return retry(tokens.size());}
  catch(ExecutionException|TimeoutException|RuntimeException e){pending.cancel(true);return retry(tokens.size());}
 }
 static List<Result> retry(int size){return Collections.nCopies(size,new Result(Outcome.RETRYABLE_FAILURE,"FCM_TRANSIENT"));}
 static Result classify(MessagingErrorCode code){
  if(code==MessagingErrorCode.UNREGISTERED)return new Result(Outcome.INVALIDATED_TOKEN,"TOKEN_INVALID");
  if(code==MessagingErrorCode.INVALID_ARGUMENT||code==MessagingErrorCode.SENDER_ID_MISMATCH||code==MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)return new Result(Outcome.PERMANENT_FAILURE,"FCM_PERMANENT");
  return new Result(Outcome.RETRYABLE_FAILURE,"FCM_TRANSIENT");
 }
}
