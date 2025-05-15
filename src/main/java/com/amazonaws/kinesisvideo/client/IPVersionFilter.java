package com.amazonaws.kinesisvideo.client;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public enum IPVersionFilter {
    IPV4_AND_IPV6,   // Allows both IPv4 and IPv6
    IPV4,            // Allows only IPv4
    IPV6;            // Allows only IPv6

    public boolean matches(final InetAddress address) {
        if (this == IPV4) {
            return address instanceof Inet4Address;
        } else if (this == IPV6) {
            return address instanceof Inet6Address;
        } else {
            return address instanceof Inet4Address || address instanceof Inet6Address;
        }
    }
}
