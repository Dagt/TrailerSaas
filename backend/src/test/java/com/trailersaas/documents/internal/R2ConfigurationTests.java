package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class R2ConfigurationTests {
  MockEnvironment valid() {
    return new MockEnvironment().withProperty("R2_ENABLED","true")
        .withProperty("R2_ACCOUNT_ID","a".repeat(32)).withProperty("R2_ENDPOINT","https://"+"a".repeat(32)+".r2.cloudflarestorage.com")
        .withProperty("R2_BUCKET","test-only-documents").withProperty("R2_ACCESS_KEY_ID","test-only-key").withProperty("R2_SECRET_ACCESS_KEY","test-only-secret");
  }
  @Test void disabledIsExplicitAndCannotIssueFakeUrls() {
    var settings=R2Configuration.settings(new MockEnvironment());assertThat(settings.enabled()).isFalse();
    var storage=new R2Configuration().objectStorage(settings);
    assertThatThrownBy(()->storage.put("key","image/jpeg",3,settings.uploadTtl())).isInstanceOfSatisfying(DocumentFailure.class,e->assertThat(e.status).isEqualTo(503));
    assertThatThrownBy(()->storage.get("key",settings.downloadTtl())).isInstanceOf(DocumentFailure.class);
    assertThatThrownBy(()->storage.exists("key")).isInstanceOf(DocumentFailure.class);
  }
  @Test void stagingAndProductionRequireR2TlsHostAndRedactedSettings() {
    for(String profile:new String[]{"staging","production"}) {
      var env=valid();env.setActiveProfiles(profile);var settings=R2Configuration.settings(env);
      assertThat(settings.enabled()).isTrue();assertThat(settings.toString()).doesNotContain("test-only-key","test-only-secret","cloudflarestorage");
    }
  }
  @ParameterizedTest @ValueSource(strings={"http://example.test","https://example.test","https://user:pass@example.test","https://example.test/?query=value","https://example.test/#fragment","https://example.test/path","http://localhost:9000","https://localhost","invalid"})
  void nonlocalUnsafeEndpointsFailWithoutLeakingValues(String value) {
    var env=valid().withProperty("R2_ENDPOINT",value);env.setActiveProfiles("staging");
    assertThatThrownBy(()->R2Configuration.settings(env)).isInstanceOf(IllegalStateException.class).hasMessage("Invalid or missing R2_ENDPOINT configuration");
  }
  @ParameterizedTest @ValueSource(strings={"R2_ACCOUNT_ID","R2_ENDPOINT","R2_BUCKET","R2_ACCESS_KEY_ID","R2_SECRET_ACCESS_KEY"})
  void enabledMissingValuesFailClosed(String key) {
    var env=valid().withProperty(key,"");assertThatThrownBy(()->R2Configuration.settings(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining(key).hasMessageNotContaining("test-only-secret");
  }
  @Test void localProtocolPeerIsOnlyAllowedOnLoopback() {
    var env=valid().withProperty("R2_ENDPOINT","http://127.0.0.1:19000");env.setActiveProfiles("local");assertThat(R2Configuration.settings(env).endpoint().getPort()).isEqualTo(19000);
  }
  @ParameterizedTest @ValueSource(strings={"DOCUMENT_MAX_SIZE_BYTES","DOCUMENT_UPLOAD_TTL_SECONDS","DOCUMENT_DOWNLOAD_TTL_SECONDS"})
  void invalidLimitsFailClosed(String key) {
    assertThatThrownBy(()->R2Configuration.settings(valid().withProperty(key,"0"))).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(()->R2Configuration.settings(valid().withProperty(key,"99999999999999"))).isInstanceOf(IllegalStateException.class);
  }
  @Test void readinessFailureNeverEchoesCredentialsEndpointOrSdkError() throws Exception {
    try(var peer=new SignedS3TestServer()) {
      var good=peer.settings();var invalid=new R2Configuration.Settings(true,good.endpoint(),good.bucket(),"invalid-test-key",good.secretKey(),good.maxBytes(),good.uploadTtl(),good.downloadTtl(),good.allowedTypes());
      assertThatThrownBy(()->new R2ObjectStorage(invalid,true)).isInstanceOf(IllegalStateException.class).hasMessage("R2 bucket readiness verification failed");
    }
  }
  @Test void mimeConfigurationCanRestrictButCannotExtendApprovedTypes() {
    assertThat(R2Configuration.settings(valid().withProperty("DOCUMENT_ALLOWED_CONTENT_TYPES","image/jpeg")).allowedTypes()).containsExactly("image/jpeg");
    for(String invalid:new String[]{"text/html","","image/jpeg,image/jpeg"})
      assertThatThrownBy(()->R2Configuration.settings(valid().withProperty("DOCUMENT_ALLOWED_CONTENT_TYPES",invalid))).isInstanceOf(IllegalStateException.class).hasMessageContaining("DOCUMENT_ALLOWED_CONTENT_TYPES");
  }
}
