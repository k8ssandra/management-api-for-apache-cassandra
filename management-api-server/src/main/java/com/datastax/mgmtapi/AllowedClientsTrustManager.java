/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

// Being signed by the CA is not enough when the CA also issues certificates for other services
public class AllowedClientsTrustManager extends X509ExtendedTrustManager {
  private static final int SAN_RFC822_NAME = 1;
  private static final int SAN_DNS_NAME = 2;
  private static final int SAN_URI = 6;
  private static final int SAN_IP_ADDRESS = 7;

  private final X509ExtendedTrustManager delegate;
  private final Set<String> allowedNames;
  // DNS names are case-insensitive (RFC 4343)
  private final Set<String> allowedDnsNames;
  // The JDK renders IP SANs in a fixed form (IPv6 uncompressed), so compare addresses, not strings
  private final Set<InetAddress> allowedIpAddresses;

  @VisibleForTesting
  AllowedClientsTrustManager(X509ExtendedTrustManager delegate, Collection<String> allowedClients) {
    this.delegate = delegate;
    this.allowedNames = ImmutableSet.copyOf(allowedClients);
    ImmutableSet.Builder<String> dnsNames = ImmutableSet.builder();
    ImmutableSet.Builder<InetAddress> ipAddresses = ImmutableSet.builder();
    for (String client : allowedClients) {
      dnsNames.add(client.toLowerCase(Locale.ROOT));
      if (InetAddresses.isInetAddress(client)) {
        ipAddresses.add(InetAddresses.forString(client));
      }
    }
    this.allowedDnsNames = dnsNames.build();
    this.allowedIpAddresses = ipAddresses.build();
  }

  public static AllowedClientsTrustManager forCaFile(File caCertFile, List<String> allowedClients)
      throws GeneralSecurityException, IOException {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    int i = 0;
    try (InputStream in = new FileInputStream(caCertFile)) {
      for (java.security.cert.Certificate cert :
          CertificateFactory.getInstance("X.509").generateCertificates(in)) {
        trustStore.setCertificateEntry("ca-" + i++, cert);
      }
    }
    // An empty trust store would reject every client instead of failing at startup like Netty does
    if (i == 0) {
      throw new CertificateException(caCertFile + " does not contain valid certificates");
    }

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);
    for (TrustManager tm : tmf.getTrustManagers()) {
      if (tm instanceof X509ExtendedTrustManager) {
        return new AllowedClientsTrustManager((X509ExtendedTrustManager) tm, allowedClients);
      }
    }
    throw new GeneralSecurityException("No X509ExtendedTrustManager available");
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType);
    checkAllowed(chain);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType, socket);
    checkAllowed(chain);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType, engine);
    checkAllowed(chain);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType, socket);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType, engine);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }

  private void checkAllowed(X509Certificate[] chain) throws CertificateException {
    if (chain == null || chain.length == 0) {
      throw new CertificateException("No client certificate presented");
    }
    List<String> identities = new ArrayList<>();
    if (isAllowed(chain[0], identities)) {
      return;
    }
    throw new CertificateException(
        "Client certificate " + identities + " is not in the list of allowed clients");
  }

  @VisibleForTesting
  boolean isAllowed(X509Certificate cert, List<String> identities)
      throws CertificateParsingException {
    try {
      for (Rdn rdn : new LdapName(cert.getSubjectX500Principal().getName()).getRdns()) {
        // Rdn.getType() only exposes the first attribute of a multi-valued RDN such as
        // CN=x+SERIALNUMBER=y
        Attribute cns = rdn.toAttributes().get("CN");
        if (cns == null) {
          continue;
        }
        NamingEnumeration<?> values = cns.getAll();
        while (values.hasMore()) {
          String cn = values.next().toString();
          identities.add(cn);
          if (allowedNames.contains(cn)) {
            return true;
          }
        }
      }
    } catch (NamingException e) {
      throw new CertificateParsingException("Unable to parse client certificate subject", e);
    }

    Collection<List<?>> sans = cert.getSubjectAlternativeNames();
    if (sans == null) {
      return false;
    }
    for (List<?> san : sans) {
      int type = (Integer) san.get(0);
      if (type != SAN_RFC822_NAME
          && type != SAN_DNS_NAME
          && type != SAN_URI
          && type != SAN_IP_ADDRESS) {
        continue;
      }
      String value = (String) san.get(1);
      identities.add(value);
      if (type == SAN_DNS_NAME) {
        if (allowedDnsNames.contains(value.toLowerCase(Locale.ROOT))) {
          return true;
        }
      } else if (type == SAN_IP_ADDRESS) {
        if (InetAddresses.isInetAddress(value)
            && allowedIpAddresses.contains(InetAddresses.forString(value))) {
          return true;
        }
      } else if (allowedNames.contains(value)) {
        return true;
      }
    }
    return false;
  }
}
