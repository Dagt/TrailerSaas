package com.trailersaas.identity.internal;

import com.trailersaas.identity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
class UserDeviceService {
 final JdbcTemplate jdbc;final TenantContext context;
 UserDeviceService(JdbcTemplate jdbc,TenantContext context){this.jdbc=jdbc;this.context=context;}
 record Registration(UUID id,UUID userId,String platform,boolean active,boolean pushRegistered,Instant lastSeenAt,Instant createdAt,Instant updatedAt){}
 Registration register(AuthenticatedUserPrincipal actor,Map<String,Object> body){
  context.establishBootstrapUser(actor);
  if(!Set.of("deviceId","platform","fcmToken","deviceName").containsAll(body.keySet()))throw new DeviceFailure(400);
  if(!(body.get("platform") instanceof String platform)||!Set.of("ANDROID","IOS").contains(platform))throw new DeviceFailure(400);
  var value=body.get("fcmToken");if(value!=null&&(!(value instanceof String s)||s.isEmpty()||s.length()>4096||s.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))))throw new DeviceFailure(400);
  var name=body.get("deviceName");if(name!=null&&(!(name instanceof String s)||s.isBlank()||s.length()>128))throw new DeviceFailure(400);
  // deviceName is validated but not retained: no unnecessary private device label.
  UUID id=null;try{if(body.get("deviceId")!=null){if(!(body.get("deviceId") instanceof String s)||!s.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new DeviceFailure(400);id=UUID.fromString(s);}}catch(IllegalArgumentException e){throw new DeviceFailure(400);}
  try{id=jdbc.queryForObject("select register_user_device(?,?,?)",UUID.class,id,platform,value);}
  catch(org.springframework.dao.DataAccessException denied){throw new DeviceFailure(409);}
  return jdbc.query("select * from user_devices where id=?",(r,n)->new Registration(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("platform"),r.getBoolean("active"),r.getString("fcm_token")!=null,r.getTimestamp("last_seen_at").toInstant(),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()),id).stream().findFirst().orElseThrow(()->new DeviceFailure(404));
 }
 void deactivate(AuthenticatedUserPrincipal actor,UUID id){context.establishBootstrapUser(actor);if(!Boolean.TRUE.equals(jdbc.queryForObject("select deactivate_user_device(?)",Boolean.class,id)))throw new DeviceFailure(404);}
}
