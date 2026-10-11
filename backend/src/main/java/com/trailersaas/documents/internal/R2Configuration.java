package com.trailersaas.documents.internal;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
class R2Configuration {
  record Settings(boolean enabled,URI endpoint,String bucket,String accessKey,String secretKey,
      long maxBytes,Duration uploadTtl,Duration downloadTtl,Set<String> allowedTypes) {
    Settings { allowedTypes=Set.copyOf(allowedTypes); }
    @Override public String toString() { return "R2 settings (credentials and endpoint redacted)"; }
  }

  @Bean Settings r2Settings(Environment env) { return settings(env); }
  static Settings settings(Environment env) {
    boolean enabled=env.getProperty("R2_ENABLED",Boolean.class,false);
    long max=number(env,"DOCUMENT_MAX_SIZE_BYTES",25L*1024*1024,1,100L*1024*1024);
    var upload=Duration.ofSeconds(number(env,"DOCUMENT_UPLOAD_TTL_SECONDS",300,1,900));
    var download=Duration.ofSeconds(number(env,"DOCUMENT_DOWNLOAD_TTL_SECONDS",60,1,300));
    Set<String> types;
    try { types=Set.of(env.getProperty("DOCUMENT_ALLOWED_CONTENT_TYPES","image/jpeg,image/png,application/pdf").split(",",-1)); }
    catch(IllegalArgumentException e) { throw invalid("DOCUMENT_ALLOWED_CONTENT_TYPES"); }
    if(types.isEmpty() || !Set.of("image/jpeg","image/png","application/pdf").containsAll(types)) throw invalid("DOCUMENT_ALLOWED_CONTENT_TYPES");
    if (!enabled) return new Settings(false,null,null,null,null,max,upload,download,types);
    String account=required(env,"R2_ACCOUNT_ID");
    String endpoint=required(env,"R2_ENDPOINT");
    String bucket=required(env,"R2_BUCKET");
    String access=required(env,"R2_ACCESS_KEY_ID"), secret=required(env,"R2_SECRET_ACCESS_KEY");
    URI uri;
    try { uri=URI.create(endpoint); } catch (IllegalArgumentException e) { throw invalid("R2_ENDPOINT"); }
    boolean local=env.getActiveProfiles().length==0 || Set.of(env.getActiveProfiles()).equals(Set.of("local"));
    boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()==null?"":uri.getHost());
    if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
        || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
        || (!"https".equals(uri.getScheme()) && !(local && loopback && "http".equals(uri.getScheme())))
        || (!loopback && (!account.matches("[0-9a-f]{32}") || !uri.getHost().equals(account+".r2.cloudflarestorage.com")))
        || (!local && (loopback || uri.getPort()!=-1))) throw invalid("R2_ENDPOINT");
    if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") || bucket.contains("..")) throw invalid("R2_BUCKET");
    return new Settings(true,uri,bucket,access,secret,max,upload,download,types);
  }
  private static long number(Environment env,String key,long fallback,long min,long max) {
    try { long n=Long.parseLong(env.getProperty(key,Long.toString(fallback))); if(n<min || n>max) throw invalid(key); return n; }
    catch(NumberFormatException e) { throw invalid(key); }
  }
  private static String required(Environment env,String key) {
    String value=env.getProperty(key); if(value==null || value.isBlank()) throw invalid(key); return value;
  }
  private static IllegalStateException invalid(String name) { return new IllegalStateException("Invalid or missing "+name+" configuration"); }

  @Bean(destroyMethod="close") ObjectStoragePort objectStorage(Settings settings) {
    if (!settings.enabled()) return new ObjectStoragePort() {
      public Signed put(String k,String t,long n,Duration d) { throw DocumentFailure.storage(); }
      public Verified copyAndVerify(String a,String b,long n) { throw DocumentFailure.storage(); }
      public Signed get(String k,Duration d) { throw DocumentFailure.storage(); }
      public boolean exists(String k) { throw DocumentFailure.storage(); }
    };
    return new R2ObjectStorage(settings,true);
  }
}
