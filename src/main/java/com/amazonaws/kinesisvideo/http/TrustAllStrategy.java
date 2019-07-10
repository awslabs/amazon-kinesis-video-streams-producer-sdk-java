package com.amazonaws.kinesisvideo.http;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.conn.ssl.TrustStrategy;

public class TrustAllStrategy implements TrustStrategy {

    @Override
    public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        //Trust all certificates
        return true;
    }
}
