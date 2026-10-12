package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import com.trailersaas.documents.EvidenceValidationException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvidenceValidationTests {
  @ParameterizedTest @ValueSource(strings={""," ","\t\r\n","\u00a0","\u2007","\u202f","\u2003"})
  void whitespaceOnlyCommentsNormalizeToMissingEvidence(String value) {
    assertThat(DatabaseOperationalEvidencePolicy.comment(value)).isNull();
  }
  @Test void meaningfulCommentPreservesTextWhileRemovingSurroundingWhitespace() {
    assertThat(DatabaseOperationalEvidencePolicy.comment("\u00a0  Receiving office closed\t\n")).isEqualTo("Receiving office closed");
    assertThat(DatabaseOperationalEvidencePolicy.comment(null)).isNull();
  }
  @Test void missingSetsAndPositionsNeverCountAsValidEvidence() {
    for(List<UUID> ids:Arrays.asList(null,List.<UUID>of(),Arrays.asList(UUID.randomUUID(),null))) {
      assertThatThrownBy(()->DatabaseOperationalEvidencePolicy.required(ids))
          .isInstanceOfSatisfying(EvidenceValidationException.class,e->assertThat(e.code()).isEqualTo(EvidenceValidationException.Code.EVIDENCE_REQUIRED));
    }
  }
  @Test void duplicateAndUnboundedSetsAreInvalidWithoutDiscardingReferences() {
    UUID id=UUID.randomUUID();
    for(var ids:List.of(List.of(id,id),java.util.stream.IntStream.range(0,51).mapToObj(i->UUID.randomUUID()).toList()))
      assertThatThrownBy(()->DatabaseOperationalEvidencePolicy.required(ids))
          .isInstanceOfSatisfying(EvidenceValidationException.class,e->assertThat(e.code()).isEqualTo(EvidenceValidationException.Code.VALIDATION_FAILED));
  }
  @Test void everyDocumentTypeUsesTheAcceptedWriteCapability() {
    for(var type:DocumentContracts.Type.values()) {
      var capability=DocumentUploadAuthority.capability(type);
      String expected=switch(type) {
        case BOL,POD,DELIVERY_PHOTO -> "POD_UPLOAD";
        case FUEL_RECEIPT -> "FUEL_EVENT_CREATE";
        case DAMAGE_PHOTO -> "EXCEPTION_REPORT";
        default -> "PRETRIP_CREATE";
      };
      assertThat(capability.name()).isEqualTo(expected);
    }
  }
}
