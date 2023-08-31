/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.resources.v2.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Comparator;
import java.util.Objects;

public final class RingRange {
  public static final Comparator<RingRange> START_COMPARATOR =
      (RingRange o1, RingRange o2) -> o1.start.compareTo(o2.start);

  @JsonProperty(value = "start", required = true)
  public final Long start;

  @JsonProperty(value = "end", required = true)
  public final Long end;

  public RingRange(
      @JsonProperty(value = "start", required = true) Long start,
      @JsonProperty(value = "end", required = true) Long end) {
    this.start = start;
    this.end = end;
  }

  public RingRange(String... range) {
    start = Long.valueOf(range[0]);
    end = Long.valueOf(range[1]);
  }

  public Long getStart() {
    return start;
  }

  public Long getEnd() {
    return end;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(start) + Objects.hashCode(end);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof RingRange)) {
      return false;
    }
    RingRange other = (RingRange) o;
    return Objects.equals(start, other.start) && Objects.equals(end, other.end);
  }
}
