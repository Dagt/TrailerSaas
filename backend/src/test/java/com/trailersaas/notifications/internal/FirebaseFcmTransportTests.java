package com.trailersaas.notifications.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

class FirebaseFcmTransportTests {
 @Test void disabledTransportNeverFakesProviderAcceptance(){
  @SuppressWarnings("unchecked") var apps=(ObjectProvider<FirebaseApp>)mock(ObjectProvider.class);
  var transport=new FirebaseFcmTransport(apps,false);
  var result=transport.send(List.of("private-token"),Map.of("kind","ALERT","tenantId",UUID.randomUUID().toString(),"alertId",UUID.randomUUID().toString()));
  assertThat(result).containsExactly(new FcmTransport.Result(FcmTransport.Outcome.RETRYABLE_FAILURE,"TRANSPORT_DISABLED"));verifyNoInteractions(apps);
 }
 @ParameterizedTest @EnumSource(MessagingErrorCode.class)
 void actualFirebaseCodesMapToSafeBoundedDiagnostics(MessagingErrorCode code){
  var result=FirebaseFcmTransport.classify(code);
  var expected=code==MessagingErrorCode.UNREGISTERED?FcmTransport.Outcome.INVALIDATED_TOKEN:
    Set.of(MessagingErrorCode.INVALID_ARGUMENT,MessagingErrorCode.SENDER_ID_MISMATCH,MessagingErrorCode.THIRD_PARTY_AUTH_ERROR).contains(code)?FcmTransport.Outcome.PERMANENT_FAILURE:FcmTransport.Outcome.RETRYABLE_FAILURE;
  assertThat(result.outcome()).isEqualTo(expected);assertThat(result.code()).isIn("TOKEN_INVALID","FCM_PERMANENT","FCM_TRANSIENT");
 }
 @Test void envelopeRejectsArbitraryPrivateDataAndUnboundedRecipients(){
  @SuppressWarnings("unchecked") var apps=(ObjectProvider<FirebaseApp>)mock(ObjectProvider.class);
  var transport=new FirebaseFcmTransport(apps,false);
  assertThatThrownBy(()->transport.send(List.of("token"),Map.of("kind","ALERT","tenantId",UUID.randomUUID().toString(),"alertId",UUID.randomUUID().toString(),"body","private"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->transport.send(Collections.nCopies(33,"token"),Map.of())).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(apps);
 }
}
