/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AllowedClientsTrustManagerTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCommonNameMatchesExactly() throws Exception {
    X509Certificate cert = cert("CN=Reaper Client,O=Example", null);

    assertTrue(allows(cert, "Reaper Client"));
    assertFalse(allows(cert, "reaper client"));
    assertFalse(allows(cert, "Reaper"));
  }

  @Test
  public void testCommonNameInMultiValuedRdnMatches() throws Exception {
    X509Certificate cert = cert("CN=allowed+SERIALNUMBER=123,O=Example", null);

    assertTrue(allows(cert, "allowed"));
  }

  @Test
  public void testDnsNameMatchesCaseInsensitively() throws Exception {
    X509Certificate cert = cert("CN=other", ImmutableList.of(san(2, "Client.Example.com")));

    assertTrue(allows(cert, "client.example.com"));
    assertFalse(allows(cert, "example.com"));
  }

  @Test
  public void testIpAddressMatchesInAnyNotation() throws Exception {
    // The JDK renders IPv6 SANs uncompressed
    X509Certificate cert =
        cert(
            "CN=other",
            ImmutableList.of(
                san(7, "0:0:0:0:0:0:0:1"), san(7, "fd00:0:0:0:0:0:0:5"), san(7, "10.0.0.1")));

    assertTrue(allows(cert, "::1"));
    assertTrue(allows(cert, "FD00::5"));
    assertTrue(allows(cert, "10.0.0.1"));
    assertFalse(allows(cert, "10.0.0.2"));
  }

  @Test
  public void testUriAndEmailMatchExactly() throws Exception {
    X509Certificate cert =
        cert(
            "CN=other",
            ImmutableList.of(san(6, "spiffe://cluster/ns/reaper"), san(1, "ops@example.com")));

    assertTrue(allows(cert, "spiffe://cluster/ns/reaper"));
    assertTrue(allows(cert, "ops@example.com"));
    assertFalse(allows(cert, "SPIFFE://cluster/ns/reaper"));
  }

  @Test
  public void testOtherSanTypesAreIgnored() throws Exception {
    // A directoryName SAN must not be matched as if it were a CN
    X509Certificate cert = cert("CN=other", ImmutableList.of(san(4, "allowed")));

    assertFalse(allows(cert, "allowed"));
  }

  @Test(expected = CertificateException.class)
  public void testCaFileWithoutCertificatesIsRejected() throws Exception {
    File emptyCa = temporaryFolder.newFile("empty-ca.pem");

    AllowedClientsTrustManager.forCaFile(emptyCa, ImmutableList.of("client"));
  }

  private static boolean allows(X509Certificate cert, String allowedClient) throws Exception {
    List<String> identities = new ArrayList<>();
    return new AllowedClientsTrustManager(null, ImmutableList.of(allowedClient))
        .isAllowed(cert, identities);
  }

  private static X509Certificate cert(String subject, Collection<List<?>> sans) throws Exception {
    X509Certificate cert = mock(X509Certificate.class);
    when(cert.getSubjectX500Principal()).thenReturn(new X500Principal(subject));
    when(cert.getSubjectAlternativeNames()).thenReturn(sans);
    return cert;
  }

  private static List<?> san(int type, String value) {
    return ImmutableList.of(type, value);
  }
}
