package com.trailersaas.notifications.internal;

import com.trailersaas.notifications.AlertRecipientPolicy;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class RecipientPolicies {
 final Map<String,AlertRecipientPolicy> policies=new HashMap<>();
 RecipientPolicies(List<AlertRecipientPolicy> policies){
  if(policies.size()>16)throw new IllegalArgumentException("Policy registration limit");
  for(var p:policies)if(p.code()==null||!p.code().matches("[A-Z][A-Z0-9_]{0,63}")||this.policies.putIfAbsent(p.code(),p)!=null)throw new IllegalArgumentException("Invalid policy registration");
 }
 boolean permits(String code,AlertRecipientPolicy.Context context,UUID user){var p=policies.get(code);return p!=null&&candidates(code,context).contains(user);}
 Set<UUID> candidates(String code,AlertRecipientPolicy.Context context){var p=policies.get(code);if(p==null)throw new IllegalArgumentException("No approved recipient policy");var ids=Set.copyOf(p.recipients(context));if(ids.size()>100)throw new IllegalArgumentException("Recipient bound exceeded");return ids;}
}
