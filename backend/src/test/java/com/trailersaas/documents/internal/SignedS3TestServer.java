package com.trailersaas.documents.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Controlled integration peer: real HTTP, SigV4 verification, bytes and copy semantics.
 * Not production storage, not a Mockito adapter. Unsupported S3 operations fail closed.
 */
final class SignedS3TestServer implements AutoCloseable {
  record Blob(byte[] bytes,String type) {}
  private final HttpServer server;
  private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
  final String access="test-"+UUID.randomUUID(), secret="test-"+UUID.randomUUID(), bucket="test-documents";
  final Map<String,Blob> objects=new ConcurrentHashMap<>();
  final AtomicInteger copies=new AtomicInteger();
  final java.util.Queue<Exception> unexpectedFailures=new ConcurrentLinkedQueue<>();
  volatile Duration timeOffset=Duration.ZERO;
  volatile boolean failCopy,failHead;
  volatile Runnable afterCopy=()->{};

  SignedS3TestServer() throws IOException {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.setExecutor(executor);server.createContext("/",this::handle);server.start();
  }
  URI endpoint() { return URI.create("http://127.0.0.1:"+server.getAddress().getPort()); }
  R2Configuration.Settings settings() { return new R2Configuration.Settings(true,endpoint(),bucket,access,secret,25*1024*1024,Duration.ofMinutes(5),Duration.ofSeconds(60),Set.of("image/jpeg","image/png","application/pdf")); }
  private void handle(HttpExchange exchange) throws IOException {
    try(exchange) {
      if(!valid(exchange)) { error(exchange,403,"SignatureDoesNotMatch");return; }
      String path=exchange.getRequestURI().getPath();String prefix="/"+bucket;
      if(path.equals(prefix) || path.equals(prefix+"/")) {
        if(exchange.getRequestMethod().equals("HEAD")) {
          if(failHead) { error(exchange,503,"ServiceUnavailable");return; }
          exchange.sendResponseHeaders(200,-1);return;
        }
        error(exchange,405,"MethodNotAllowed");return;
      }
      if(!path.startsWith(prefix+"/")) { error(exchange,404,"NoSuchBucket");return; }
      String key=path.substring(prefix.length()+1);
      switch(exchange.getRequestMethod()) {
        case "PUT" -> {
          String source=exchange.getRequestHeaders().getFirst("x-amz-copy-source");
          if(source!=null) {
            if(failCopy) { error(exchange,503,"ServiceUnavailable");return; }
            source=URLDecoder.decode(source,StandardCharsets.UTF_8);
            if(source.startsWith("/")) source=source.substring(1);
            var blob=objects.get(source.substring(bucket.length()+1));
            if(blob==null) { error(exchange,404,"NoSuchKey");return; }
            objects.put(key,new Blob(blob.bytes().clone(),blob.type()));copies.incrementAndGet();afterCopy.run();
            byte[] xml=("<CopyObjectResult><LastModified>2026-01-01T00:00:00Z</LastModified><ETag>\""+etag(blob)+"\"</ETag></CopyObjectResult>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/xml");exchange.sendResponseHeaders(200,xml.length);exchange.getResponseBody().write(xml);
          } else {
            byte[] bytes=exchange.getRequestBody().readNBytes(1024*1024+1);
            if(bytes.length>1024*1024) { error(exchange,413,"EntityTooLarge");return; }
            var blob=new Blob(bytes,exchange.getRequestHeaders().getFirst("Content-Type"));objects.put(key,blob);
            exchange.getResponseHeaders().set("ETag","\""+etag(blob)+"\"");exchange.sendResponseHeaders(200,-1);
          }
        }
        case "HEAD","GET" -> {
          if(failHead) { error(exchange,503,"ServiceUnavailable");return; }
          var blob=objects.get(key);if(blob==null) { error(exchange,404,"NoSuchKey");return; }
          exchange.getResponseHeaders().set("Content-Type",blob.type());
          exchange.getResponseHeaders().set("Content-Length",Integer.toString(blob.bytes().length));
          exchange.getResponseHeaders().set("ETag","\""+etag(blob)+"\"");
          exchange.getResponseHeaders().set("Last-Modified","Thu, 01 Jan 2026 00:00:00 GMT");
          boolean head=exchange.getRequestMethod().equals("HEAD");exchange.sendResponseHeaders(200,head?-1:blob.bytes().length);
          if(!head) exchange.getResponseBody().write(blob.bytes());
        }
        default -> error(exchange,405,"MethodNotAllowed");
      }
    } catch(Exception e) { unexpectedFailures.add(e); }
  }
  private boolean valid(HttpExchange e) {
    try {
      var query=new TreeMap<String,String>();
      String raw=e.getRequestURI().getRawQuery();
      if(raw!=null) for(String entry:raw.split("&")) {
        var parts=entry.split("=",2);query.put(decode(parts[0]),parts.length==2?decode(parts[1]):"");
      }
      String credential,headers,signature,date,payload;
      if(query.containsKey("X-Amz-Signature")) {
        signature=query.remove("X-Amz-Signature");credential=query.get("X-Amz-Credential");headers=query.get("X-Amz-SignedHeaders");date=query.get("X-Amz-Date");payload="UNSIGNED-PAYLOAD";
        if(!"AWS4-HMAC-SHA256".equals(query.get("X-Amz-Algorithm"))) return false;
        Instant created=LocalDateTime.parse(date,DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).toInstant(ZoneOffset.UTC);
        var now=Instant.now().plus(timeOffset);
        if(now.isAfter(created.plusSeconds(Long.parseLong(query.get("X-Amz-Expires")))) || now.isBefore(created.minusSeconds(60))) return false;
      } else {
        String auth=e.getRequestHeaders().getFirst("Authorization");
        if(auth==null || !auth.startsWith("AWS4-HMAC-SHA256 ")) return false;
        var values=new HashMap<String,String>();
        for(String item:auth.substring(17).split(",")) {var pair=item.trim().split("=",2);values.put(pair[0],pair[1]);}
        credential=values.get("Credential");headers=values.get("SignedHeaders");signature=values.get("Signature");date=e.getRequestHeaders().getFirst("x-amz-date");payload=e.getRequestHeaders().getFirst("x-amz-content-sha256");
        if(payload==null) payload=sha(new byte[0]);
      }
      if(credential==null || !credential.startsWith(access+"/")) return false;
      // S3 CopyObject requires x-amz-copy-source to be signed. A plain PUT grant
      // cannot be promoted to a server-side copy of a different private object.
      if(e.getRequestHeaders().containsKey("x-amz-copy-source")
          && !Arrays.asList(headers.split(";")).contains("x-amz-copy-source")) return false;
      String scope=credential.substring(access.length()+1);
      StringBuilder canonicalHeaders=new StringBuilder();
      for(String name:headers.split(";")) {
        String value=e.getRequestHeaders().getFirst(name);if(value==null) return false;
        canonicalHeaders.append(name).append(':').append(value.trim().replaceAll("\\s+"," ")).append('\n');
      }
      String canonicalQuery=query.entrySet().stream().map(x->encode(x.getKey())+"="+encode(x.getValue())).collect(java.util.stream.Collectors.joining("&"));
      String canonical=e.getRequestMethod()+"\n"+e.getRequestURI().getRawPath()+"\n"+canonicalQuery+"\n"+canonicalHeaders+"\n"+headers+"\n"+payload;
      String stringToSign="AWS4-HMAC-SHA256\n"+date+"\n"+scope+"\n"+sha(canonical.getBytes(StandardCharsets.UTF_8));
      var parts=scope.split("/");
      if(parts.length!=4 || !parts[1].equals("auto") || !parts[2].equals("s3") || !parts[3].equals("aws4_request")) return false;
      byte[] key=hmac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),parts[0]);key=hmac(key,parts[1]);key=hmac(key,parts[2]);key=hmac(key,parts[3]);
      return MessageDigest.isEqual(HexFormat.of().parseHex(signature),hmac(key,stringToSign));
    } catch(Exception e1) { return false; }
  }
  private static byte[] hmac(byte[] key,String input) throws Exception {
    Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
  }
  private static String decode(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
  private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20").replace("%7E","~"); }
  static String sha(byte[] bytes) { return HexFormat.of().formatHex(R2ObjectStorage.sha256().digest(bytes)); }
  private static String etag(Blob blob) { return sha(blob.bytes()).substring(0,32); }
  private static void error(HttpExchange e,int status,String code) throws IOException {
    byte[] xml=("<Error><Code>"+code+"</Code><Message>Test protocol failure</Message></Error>").getBytes(StandardCharsets.UTF_8);
    if(e.getRequestMethod().equals("HEAD")) { e.sendResponseHeaders(status,-1);return; }
    e.getResponseHeaders().set("Content-Type","application/xml");e.sendResponseHeaders(status,xml.length);e.getResponseBody().write(xml);
  }
  public void close() {
    server.stop(0);executor.close();
    if(!unexpectedFailures.isEmpty()) throw new AssertionError("Unexpected S3 test peer failure",unexpectedFailures.peek());
  }
}
