package com.datastax.mgmtapi.resources.v2.models;

import java.math.BigInteger;
import java.util.Comparator;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class RingRange {
  public static final Comparator<RingRange> START_COMPARATOR
      = (RingRange o1, RingRange o2) -> o1.start.compareTo(o2.start);

  @JsonProperty(value = "start", required = true)
  public final BigInteger start;
  @JsonProperty(value = "end", required = true)
  public final BigInteger end;

  public RingRange(
      @JsonProperty(value = "start", required = true) BigInteger start,
      @JsonProperty(value = "end", required = true) BigInteger end) {
    this.start = start;
    this.end = end;
  }

  public RingRange(String... range) {
    start = new BigInteger(range[0]);
    end = new BigInteger(range[1]);
  }

  public BigInteger getStart() {
    return start;
  }

  public BigInteger getEnd() {
    return end;
  }
}