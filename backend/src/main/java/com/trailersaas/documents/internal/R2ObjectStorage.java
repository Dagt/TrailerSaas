package com.trailersaas.documents.internal;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

/** R2 S3/Signature V4 adapter. SHA-256 is computed from bytes, never client metadata. */
final class R2ObjectStorage implements ObjectStoragePort {
  private final S3Client client;
  private final S3Presigner signer;
  private final String bucket;

  R2ObjectStorage(R2Configuration.Settings config,boolean verifyBucket) {
    bucket=config.bucket();
    var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey(),config.secretKey()));
    var service=S3Configuration.builder().pathStyleAccessEnabled(true).build();
    signer=S3Presigner.builder().region(Region.of("auto")).endpointOverride(config.endpoint())
        .credentialsProvider(credentials).serviceConfiguration(service).build();
    client=S3Client.builder().region(Region.of("auto")).endpointOverride(config.endpoint())
        .credentialsProvider(credentials).serviceConfiguration(service)
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
        .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(45))
            .apiCallAttemptTimeout(Duration.ofSeconds(30)).retryStrategy(b->b.maxAttempts(1)).build()).build();
    if(verifyBucket) {
      try { client.headBucket(b->b.bucket(bucket)); }
      catch (RuntimeException e) { close(); throw new IllegalStateException("R2 bucket readiness verification failed"); }
    }
  }

  public Signed put(String key,String type,long size,Duration lifetime) {
    try {
      client.headBucket(b->b.bucket(bucket));
      var request=PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).contentLength(size).build();
      var signed=signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(lifetime).putObjectRequest(request).build());
      return new Signed(signed.url().toString(),signed.expiration());
    } catch(RuntimeException e) { throw DocumentFailure.storage(); }
  }

  public Verified copyAndVerify(String temporary,String publication,long expectedSize) {
    try {
      HeadObjectResponse source;
      try { source=client.headObject(b->b.bucket(bucket).key(temporary)); }
      catch(S3Exception e) { if(e.statusCode()==404) throw DocumentFailure.conflict("UPLOAD_NOT_COMPLETE"); throw e; }
      if(source.contentLength()!=expectedSize) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
      client.copyObject(CopyObjectRequest.builder().bucket(bucket).key(publication).copySource(bucket+"/"+temporary).build());
      var head=client.headObject(b->b.bucket(bucket).key(publication));
      if(head.contentLength()!=expectedSize) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
      var digest=sha256();
      long bytes=0;
      try(var stream=client.getObject(b->b.bucket(bucket).key(publication))) {
        byte[] block=new byte[8192]; int count;
        while((count=stream.read(block))!=-1) {
          bytes+=count;
          if(bytes>expectedSize) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
          digest.update(block,0,count);
        }
      }
      if(bytes!=expectedSize) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
      return new Verified(bytes,head.contentType(),HexFormat.of().formatHex(digest.digest()));
    } catch(DocumentFailure e) { throw e; }
    catch(RuntimeException | IOException e) { throw DocumentFailure.storage(); }
  }
  static MessageDigest sha256() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
  }
  public Signed get(String key,Duration lifetime) {
    try {
      var signed=signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(lifetime)
          .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).responseContentDisposition("attachment").build()).build());
      return new Signed(signed.url().toString(),signed.expiration());
    } catch(RuntimeException e) { throw DocumentFailure.storage(); }
  }
  public boolean exists(String key) {
    try { client.headObject(b->b.bucket(bucket).key(key)); return true; }
    catch(S3Exception e) { if(e.statusCode()==404) return false; throw DocumentFailure.storage(); }
    catch(RuntimeException e) { throw DocumentFailure.storage(); }
  }
  @Override public void close() { signer.close(); client.close(); }
}
