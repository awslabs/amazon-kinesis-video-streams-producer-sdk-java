package com.amazonaws.kinesisvideo.client;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * An enumeration that defines IP version filtering options for network connections.
 * This filter can be used to restrict network connections to specific IP protocol versions.
 * 
 * <p>The filter supports three modes:
 * <ul>
 *   <li>{@link #IPV4_AND_IPV6} - Allows both IPv4 and IPv6 addresses</li>
 *   <li>{@link #IPV4} - Allows only IPv4 addresses</li>
 *   <li>{@link #IPV6} - Allows only IPv6 addresses</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * IPVersionFilter filter = IPVersionFilter.IPV4;
 * InetAddress address = InetAddress.getByName("192.168.1.1");
 * if (filter.matches(address)) {
 *     // Address is allowed by the filter
 * }
 * }</pre>
 */
@ThreadSafe
public enum IPVersionFilter {
    
    /**
     * Allows both IPv4 and IPv6 addresses.
     * This is the most permissive filter option.
     */
    IPV4_AND_IPV6,
    
    /**
     * Allows only IPv4 addresses.
     * IPv6 addresses will be rejected by this filter.
     */
    IPV4,
    
    /**
     * Allows only IPv6 addresses.
     * IPv4 addresses will be rejected by this filter.
     */
    IPV6;

    /**
     * Determines whether the given InetAddress matches this IP version filter.
     * 
     * <p>The matching logic is as follows:
     * <ul>
     *   <li>For {@link #IPV4}: Returns {@code true} only if the address is an instance of {@link Inet4Address}</li>
     *   <li>For {@link #IPV6}: Returns {@code true} only if the address is an instance of {@link Inet6Address}</li>
     *   <li>For {@link #IPV4_AND_IPV6}: Returns {@code true} if the address is either IPv4 or IPv6</li>
     * </ul>
     * 
     * @param address the InetAddress to check against this filter.
     * @return {@code true} if the address matches this filter's criteria, {@code false} otherwise ({@code null} will be {@code false}).
     *
     * @see Inet4Address
     * @see Inet6Address
     */
    public boolean matches(@Nullable final InetAddress address) {
        if (this == IPV4) {
            return address instanceof Inet4Address;
        } else if (this == IPV6) {
            return address instanceof Inet6Address;
        } else {
            return address instanceof Inet4Address || address instanceof Inet6Address;
        }
    }
}
