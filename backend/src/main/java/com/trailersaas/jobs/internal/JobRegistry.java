package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.*;
import com.trailersaas.jobs.JobFailure.Code;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
class JobRegistry {
    private final Map<Key, JobHandler<?>> handlers = new HashMap<>();
    private final JsonMapper json = JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    record Key(String name, int version) {}
    JobRegistry(List<JobHandler<?>> registrations) {
        if (registrations.size()>32) throw new IllegalArgumentException("Too many job handlers");
        for (var handler : registrations) {
            var t=handler.type();
            if (handlers.putIfAbsent(new Key(t.name(),t.schemaVersion()),handler)!=null) {
                throw new IllegalArgumentException("Duplicate job handler");
            }
        }
    }
    <P> JobHandler<P> registered(JobType<P> type) {
        var handler = lookup(type.name(), type.schemaVersion());
        if (!handler.type().equals(type)) throw new JobFailure(Code.INVALID_PAYLOAD,false);
        @SuppressWarnings("unchecked") var typed = (JobHandler<P>)handler;
        return typed;
    }
    private JobHandler<?> lookup(String name, int version) {
        var handler=handlers.get(new Key(name,version));
        if (handler!=null) return handler;
        throw new JobFailure(handlers.keySet().stream().anyMatch(k -> k.name().equals(name))
                ? Code.UNSUPPORTED_PAYLOAD_VERSION : Code.UNKNOWN_JOB_TYPE,false);
    }
    <P> String encode(JobType<P> type, P payload) {
        registered(type);
        if (!type.payloadClass().isInstance(payload)) throw new JobFailure(Code.INVALID_PAYLOAD,false);
        var data=json.valueToTree(payload);
        var text=json.writeValueAsString(Map.of("schemaVersion",type.schemaVersion(),"data",data));
        if (!data.isObject() || text.getBytes(StandardCharsets.UTF_8).length>16384) throw new JobFailure(Code.INVALID_PAYLOAD,false);
        decode(type.name(),text); // Strict round trip before inserting.
        return text;
    }
    record Decoded<P>(JobHandler<P> handler, P payload) {}
    Decoded<?> decode(String name, String payload) {
        try {
            var root=json.readTree(payload);
            if (!root.isObject() || root.size()!=2 || !root.has("schemaVersion") ||
                    !root.has("data") || !root.get("schemaVersion").isIntegralNumber() || !root.get("data").isObject()) {
                throw new JobFailure(Code.INVALID_PAYLOAD,false);
            }
            return decodeData(lookup(name,root.get("schemaVersion").intValue()),root.get("data"));
        } catch (JobFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw new JobFailure(Code.INVALID_PAYLOAD,false); }
    }
    private <P> Decoded<P> decodeData(JobHandler<P> handler, tools.jackson.databind.JsonNode data) {
        return new Decoded<>(handler,json.treeToValue(data,handler.type().payloadClass()));
    }
    static void aggregate(JobType<?> type, JobAggregate aggregate) {
        if (!Objects.equals(type.aggregateType(), aggregate==null ? null : aggregate.type())) {
            throw new JobFailure(Code.INVALID_SCOPE,false);
        }
    }
}
