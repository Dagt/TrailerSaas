package com.trailersaas.orders;

import java.util.Map;

/** Shared frozen destination shape; contains no entity or authorization semantics. */
public interface OrderDestinationSnapshots {
  Map<String, Object> freeze(Object input);
}
