package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;

class R2ObjectStorageTests {
  SignedS3TestServer peer;
  R2ObjectStorage storage;
  @BeforeEach void open() throws Exception { peer=new SignedS3TestServer();storage=new R2ObjectStorage(peer.settings(),true); }
  @AfterEach void close() { storage.close();peer.close(); }
  HttpResponse<byte[]> request(String method,String url,String type,byte[] bytes) throws Exception {
    var builder=HttpRequest.newBuilder(URI.create(url));if(type!=null) builder.header("Content-Type",type);
    return HttpClient.newHttpClient().send(builder.method(method,bytes==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofByteArray());
  }
  @Test void signedPutHeadCopyVerifyAndPrivateGetUseRealProtocol() throws Exception {
    byte[] bytes="signed bytes".getBytes();var upload=storage.put("temporary","image/jpeg",bytes.length,Duration.ofMinutes(5));
    assertThat(upload.url()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256").doesNotContain(peer.secret);
    assertThat(request("PUT",upload.url(),"image/jpeg",bytes).statusCode()).isEqualTo(200);
    assertThat(storage.exists("temporary")).isTrue();
    assertThat(storage.copyAndVerify("temporary","published",bytes.length)).isEqualTo(new ObjectStoragePort.Verified(bytes.length,"image/jpeg",SignedS3TestServer.sha(bytes)));
    var download=storage.get("published",Duration.ofSeconds(60));
    assertThat(request("GET",download.url(),null,null).body()).isEqualTo(bytes);
    assertThat(request("GET",peer.endpoint()+"/"+peer.bucket+"/published",null,null).statusCode()).isEqualTo(403);
  }
  @Test void putSignatureRestrictsMethodKeyContentTypeAndLength() throws Exception {
    var upload=storage.put("bound","image/jpeg",3,Duration.ofMinutes(5));
    assertThat(request("GET",upload.url(),"image/jpeg",null).statusCode()).isEqualTo(403);
    assertThat(request("PUT",upload.url().replace("/bound?","/other?"),"image/jpeg",new byte[3]).statusCode()).isEqualTo(403);
    assertThat(request("PUT",upload.url(),"image/png",new byte[3]).statusCode()).isEqualTo(403);
    assertThat(request("PUT",upload.url(),"image/jpeg",new byte[4]).statusCode()).isEqualTo(403);
  }
  @Test void expiredSignaturesFailWithoutRealTimers() throws Exception {
    var upload=storage.put("expired","image/jpeg",3,Duration.ofSeconds(1));
    peer.timeOffset=Duration.ofSeconds(3);
    assertThat(request("PUT",upload.url(),"image/jpeg",new byte[3]).statusCode()).isEqualTo(403);
  }
  @Test void reusableTemporaryPutCannotOverwritePublishedEvidence() throws Exception {
    var upload=storage.put("pending","image/jpeg",3,Duration.ofMinutes(5));
    request("PUT",upload.url(),"image/jpeg",new byte[]{1,2,3});storage.copyAndVerify("pending","immutable",3);
    assertThat(request("PUT",upload.url(),"image/jpeg",new byte[]{9,8,7}).statusCode()).isEqualTo(200);
    assertThat(request("GET",storage.get("immutable",Duration.ofMinutes(1)).url(),null,null).body()).containsExactly(1,2,3);
  }
  @Test void missingObjectAndSizeMismatchAreDistinctFromInfrastructure() {
    assertThatThrownBy(()->storage.copyAndVerify("missing","target",3)).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.code).isEqualTo("UPLOAD_NOT_COMPLETE"));
    peer.objects.put("wrong",new SignedS3TestServer.Blob(new byte[2],"image/jpeg"));
    assertThatThrownBy(()->storage.copyAndVerify("wrong","target",3)).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.code).isEqualTo("UPLOAD_CONTENT_MISMATCH"));
    peer.failHead=true;
    assertThatThrownBy(()->storage.exists("wrong")).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.status).isEqualTo(503));
  }
  @Test void hashComesFromActualBytesNotClientMetadataOrEtag() {
    byte[] bytes=UUID.randomUUID().toString().getBytes();peer.objects.put("original",new SignedS3TestServer.Blob(bytes,"image/png"));
    assertThat(storage.copyAndVerify("original","copy",bytes.length).sha256()).isEqualTo(SignedS3TestServer.sha(bytes));
  }
  @Test void failureAfterCopyLeavesPrivateUnpublishedCandidateAndRetryUsesNewKey() {
    peer.objects.put("stage",new SignedS3TestServer.Blob(new byte[3],"image/jpeg"));
    peer.afterCopy=()->peer.failHead=true;
    assertThatThrownBy(()->storage.copyAndVerify("stage","candidate1",3)).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.status).isEqualTo(503));
    assertThat(peer.objects).containsKey("candidate1");peer.afterCopy=()->{};peer.failHead=false;
    assertThat(storage.copyAndVerify("stage","candidate2",3).size()).isEqualTo(3);
    assertThat(peer.objects).containsKeys("candidate1","candidate2");
  }
  @Test void failedCopyNeverReportsVerificationOrPublication() {
    peer.objects.put("stage",new SignedS3TestServer.Blob(new byte[3],"image/jpeg"));peer.failCopy=true;
    assertThatThrownBy(()->storage.copyAndVerify("stage","unpublished",3)).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.status).isEqualTo(503));
    assertThat(peer.objects).doesNotContainKey("unpublished");
  }
  @Test void downloadGrantRestrictsMethodAndExpiresWithoutSleeping() throws Exception {
    peer.objects.put("document",new SignedS3TestServer.Blob(new byte[3],"image/jpeg"));
    var signed=storage.get("document",Duration.ofSeconds(60));
    assertThat(request("PUT",signed.url(),"image/jpeg",new byte[3]).statusCode()).isEqualTo(403);
    assertThat(request("GET",signed.url(),null,null).statusCode()).isEqualTo(200);
    peer.timeOffset=Duration.ofSeconds(62);assertThat(request("GET",signed.url(),null,null).statusCode()).isEqualTo(403);
  }
}
