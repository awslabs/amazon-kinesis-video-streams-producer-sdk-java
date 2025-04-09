package com.amazonaws.kinesisvideo.http;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import org.apache.http.conn.DnsResolver;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class KvsFilteredDnsResolverTest {

    @Test(expected = UnknownHostException.class)
    public void testResolve_withNonMatchingFilter_throwsException() throws Exception {
        final KvsFilteredDnsResolver resolver = new KvsFilteredDnsResolver(IPVersionFilter.IPV4);

        final DnsResolver fakeResolver = (host) -> new InetAddress[]{
                Inet6Address.getByName("::1") // Mock IPv6 address
        };

        resolver.setDnsResolver(fakeResolver);

        resolver.resolve("https://kinesisvideo.us-west-2.amazonaws.com");
    }
}