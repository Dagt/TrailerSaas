package com.trailersaas.orders.internal;

import com.trailersaas.orders.OrderDestinationSnapshots;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class SharedOrderDestinationSnapshots implements OrderDestinationSnapshots {
  public Map<String, Object> freeze(Object input) {
    try {
      return OrderValidation.snapshot(input);
    } catch (OrderFailure failure) {
      throw new IllegalArgumentException("Invalid destination snapshot");
    }
  }
}
