package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;

abstract class YardCheckFixture extends YardPostgresFixture {
  HttpResponse<String> command(String path,String key,Map<String,Object> body) throws Exception {
    var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+path)).header("Authorization","Bearer yard").header("Content-Type","application/json");
    if(key!=null)r.header("Idempotency-Key",key);
    return HttpClient.newHttpClient().send(r.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  Map<String,Object> startBody(UUID location) {var r=new LinkedHashMap<String,Object>();r.put("siteId",site.toString());r.put("locationId",location==null?null:location.toString());return r;}
  HttpResponse<String> start() throws Exception {return command("/yard-checks",UUID.randomUUID().toString(),startBody(null));}
  UUID check(HttpResponse<String> r) {assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return UUID.fromString(JsonPath.read(r.body(),"$.check.id"));}
  Map<String,Object> scanBody(UUID asset,UUID location) {return new LinkedHashMap<>(Map.of("trailerId",asset.toString(),"observedLocationId",location.toString(),"scannedAt",Instant.now().toString()));}
  HttpResponse<String> scan(UUID id,String key,Map<String,Object> body) throws Exception {return command("/yard-checks/"+id+"/scans",key,body);}
}
