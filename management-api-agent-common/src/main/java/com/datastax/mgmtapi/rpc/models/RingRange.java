/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

package com.datastax.mgmtapi.rpc.models;

import java.math.BigInteger;
import java.util.Comparator;

public final class RingRange {

  public static final Comparator<RingRange> START_COMPARATOR
      = (RingRange o1, RingRange o2) -> o1.start.compareTo(o2.start);

  public final BigInteger start;
  public final BigInteger end;

  public RingRange(BigInteger start, BigInteger end) {
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