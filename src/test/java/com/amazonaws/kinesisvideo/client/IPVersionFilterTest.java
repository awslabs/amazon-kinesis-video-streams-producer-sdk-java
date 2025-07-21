package com.amazonaws.kinesisvideo.client;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link IPVersionFilter} enum.
 * Tests cover all enum values and all branches in the matches method.
 */
public class IPVersionFilterTest {

    // ========== IPV4 Filter Tests ==========

    @Test
    public void whenIPV4FilterMatchesInet4Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress ipv4Address = InetAddress.getByName("192.168.1.1");

        // When
        final boolean result = filter.matches(ipv4Address);

        // Then
        assertTrue("IPV4 filter should match Inet4Address", result);
        assertTrue("Address should be instance of Inet4Address", ipv4Address instanceof Inet4Address);
    }

    @Test
    public void whenIPV4FilterMatchesInet6Address_thenReturnsFalse() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress ipv6Address = InetAddress.getByName("::1");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertFalse("IPV4 filter should not match Inet6Address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    @Test
    public void whenIPV4FilterMatchesRealIPv4Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress ipv4Address = InetAddress.getByName("8.8.8.8");

        // When
        final boolean result = filter.matches(ipv4Address);

        // Then
        assertTrue("IPV4 filter should match real IPv4 address", result);
        assertTrue("Address should be instance of Inet4Address", ipv4Address instanceof Inet4Address);
    }

    @Test
    public void whenIPV4FilterMatchesLocalhostIPv4_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress localhostIPv4 = InetAddress.getByName("127.0.0.1");

        // When
        final boolean result = filter.matches(localhostIPv4);

        // Then
        assertTrue("IPV4 filter should match localhost IPv4", result);
        assertTrue("Address should be instance of Inet4Address", localhostIPv4 instanceof Inet4Address);
    }

    @Test
    public void whenIPV4FilterMatchesPrivateIPv4Addresses_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;

        // When & Then - Test various private IPv4 ranges
        assertTrue("Should match 10.x.x.x", filter.matches(InetAddress.getByName("10.0.0.1")));
        assertTrue("Should match 172.16.x.x", filter.matches(InetAddress.getByName("172.16.0.1")));
        assertTrue("Should match 192.168.x.x", filter.matches(InetAddress.getByName("192.168.0.1")));
    }

    // ========== IPV6 Filter Tests ==========

    @Test
    public void whenIPV6FilterMatchesInet6Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;
        final InetAddress ipv6Address = InetAddress.getByName("::1");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertTrue("IPV6 filter should match Inet6Address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    @Test
    public void whenIPV6FilterMatchesInet4Address_thenReturnsFalse() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;
        final InetAddress ipv4Address = InetAddress.getByName("192.168.1.1");

        // When
        final boolean result = filter.matches(ipv4Address);

        // Then
        assertFalse("IPV6 filter should not match Inet4Address", result);
        assertTrue("Address should be instance of Inet4Address", ipv4Address instanceof Inet4Address);
    }

    @Test
    public void whenIPV6FilterMatchesRealIPv6Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;
        final InetAddress ipv6Address = InetAddress.getByName("::1");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertTrue("IPV6 filter should match real IPv6 address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    @Test
    public void whenIPV6FilterMatchesFullIPv6Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;
        final InetAddress ipv6Address = InetAddress.getByName("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertTrue("IPV6 filter should match full IPv6 address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    @Test
    public void whenIPV6FilterMatchesVariousIPv6Formats_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;

        // When & Then - Test various IPv6 formats
        assertTrue("Should match loopback", filter.matches(InetAddress.getByName("::1")));
        assertTrue("Should match full format", filter.matches(InetAddress.getByName("2001:db8::1")));
        assertTrue("Should match compressed format", filter.matches(InetAddress.getByName("fe80::1")));
    }

    // ========== IPV4_AND_IPV6 Filter Tests ==========

    @Test
    public void whenIPV4AndIPV6FilterMatchesInet4Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4_AND_IPV6;
        final InetAddress ipv4Address = InetAddress.getByName("192.168.1.1");

        // When
        final boolean result = filter.matches(ipv4Address);

        // Then
        assertTrue("IPV4_AND_IPV6 filter should match Inet4Address", result);
        assertTrue("Address should be instance of Inet4Address", ipv4Address instanceof Inet4Address);
    }

    @Test
    public void whenIPV4AndIPV6FilterMatchesInet6Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4_AND_IPV6;
        final InetAddress ipv6Address = InetAddress.getByName("::1");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertTrue("IPV4_AND_IPV6 filter should match Inet6Address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    @Test
    public void whenIPV4AndIPV6FilterMatchesRealIPv4Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4_AND_IPV6;
        final InetAddress ipv4Address = InetAddress.getByName("8.8.8.8");

        // When
        final boolean result = filter.matches(ipv4Address);

        // Then
        assertTrue("IPV4_AND_IPV6 filter should match real IPv4 address", result);
        assertTrue("Address should be instance of Inet4Address", ipv4Address instanceof Inet4Address);
    }

    @Test
    public void whenIPV4AndIPV6FilterMatchesRealIPv6Address_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4_AND_IPV6;
        final InetAddress ipv6Address = InetAddress.getByName("2001:4860:4860::8888");

        // When
        final boolean result = filter.matches(ipv6Address);

        // Then
        assertTrue("IPV4_AND_IPV6 filter should match real IPv6 address", result);
        assertTrue("Address should be instance of Inet6Address", ipv6Address instanceof Inet6Address);
    }

    // ========== Enum Values Tests ==========

    @Test
    public void whenGettingAllEnumValues_thenContainsAllThreeValues() {
        // Given & When
        final IPVersionFilter[] values = IPVersionFilter.values();

        // Then
        assertEquals("Should have exactly 3 enum values", 3, values.length);
        assertTrue("Should contain IPV4_AND_IPV6", containsValue(values, IPVersionFilter.IPV4_AND_IPV6));
        assertTrue("Should contain IPV4", containsValue(values, IPVersionFilter.IPV4));
        assertTrue("Should contain IPV6", containsValue(values, IPVersionFilter.IPV6));
    }

    @Test
    public void whenGettingEnumValueByName_thenReturnsCorrectValue() {
        // Given & When & Then
        assertEquals("Should return IPV4_AND_IPV6", IPVersionFilter.IPV4_AND_IPV6,
                IPVersionFilter.valueOf("IPV4_AND_IPV6"));
        assertEquals("Should return IPV4", IPVersionFilter.IPV4,
                IPVersionFilter.valueOf("IPV4"));
        assertEquals("Should return IPV6", IPVersionFilter.IPV6,
                IPVersionFilter.valueOf("IPV6"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenGettingEnumValueByInvalidName_thenThrowsIllegalArgumentException() {
        // Given & When & Then
        IPVersionFilter.valueOf("INVALID_VALUE");
    }

    // ========== Comprehensive Branch Coverage Tests ==========

    @Test
    public void whenTestingAllBranchesInMatchesMethod_thenAllPathsAreCovered() throws UnknownHostException {
        // This test ensures we've covered all branches in the matches method

        // Branch 1: this == IPV4 && address instanceof Inet4Address -> true
        assertTrue("IPV4 filter with IPv4 address should return true",
                IPVersionFilter.IPV4.matches(InetAddress.getByName("1.1.1.1")));

        // Branch 2: this == IPV4 && !(address instanceof Inet4Address) -> false
        assertFalse("IPV4 filter with IPv6 address should return false",
                IPVersionFilter.IPV4.matches(InetAddress.getByName("::1")));

        // Branch 3: this == IPV6 && address instanceof Inet6Address -> true
        assertTrue("IPV6 filter with IPv6 address should return true",
                IPVersionFilter.IPV6.matches(InetAddress.getByName("::1")));

        // Branch 4: this == IPV6 && !(address instanceof Inet6Address) -> false
        assertFalse("IPV6 filter with IPv4 address should return false",
                IPVersionFilter.IPV6.matches(InetAddress.getByName("1.1.1.1")));

        // Branch 5: this == IPV4_AND_IPV6 && address instanceof Inet4Address -> true
        assertTrue("IPV4_AND_IPV6 filter with IPv4 address should return true",
                IPVersionFilter.IPV4_AND_IPV6.matches(InetAddress.getByName("1.1.1.1")));

        // Branch 6: this == IPV4_AND_IPV6 && address instanceof Inet6Address -> true
        assertTrue("IPV4_AND_IPV6 filter with IPv6 address should return true",
                IPVersionFilter.IPV4_AND_IPV6.matches(InetAddress.getByName("::1")));
    }

    // ========== Consistency Tests ==========

    @Test
    public void whenAllFiltersTestedWithSameAddresses_thenBehaviorIsConsistent() throws UnknownHostException {
        // Given
        final InetAddress ipv4 = InetAddress.getByName("203.0.113.1");
        final InetAddress ipv6 = InetAddress.getByName("2001:db8::8a2e:370:7334");

        // When & Then - Test consistency across all filters
        // IPv4 address tests
        assertTrue("IPV4 should match IPv4 address", IPVersionFilter.IPV4.matches(ipv4));
        assertFalse("IPV6 should not match IPv4 address", IPVersionFilter.IPV6.matches(ipv4));
        assertTrue("IPV4_AND_IPV6 should match IPv4 address", IPVersionFilter.IPV4_AND_IPV6.matches(ipv4));

        // IPv6 address tests
        assertFalse("IPV4 should not match IPv6 address", IPVersionFilter.IPV4.matches(ipv6));
        assertTrue("IPV6 should match IPv6 address", IPVersionFilter.IPV6.matches(ipv6));
        assertTrue("IPV4_AND_IPV6 should match IPv6 address", IPVersionFilter.IPV4_AND_IPV6.matches(ipv6));
    }

    // ========== Edge Cases Tests ==========

    @Test
    public void whenIPV4FilterMatchesZeroAddress_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress zeroAddress = InetAddress.getByName("0.0.0.0");

        // When
        final boolean result = filter.matches(zeroAddress);

        // Then
        assertTrue("IPV4 filter should match 0.0.0.0", result);
        assertTrue("Address should be instance of Inet4Address", zeroAddress instanceof Inet4Address);
    }

    @Test
    public void whenIPV4FilterMatchesBroadcastAddress_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;
        final InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");

        // When
        final boolean result = filter.matches(broadcastAddress);

        // Then
        assertTrue("IPV4 filter should match 255.255.255.255", result);
        assertTrue("Address should be instance of Inet4Address", broadcastAddress instanceof Inet4Address);
    }

    @Test
    public void whenIPV6FilterMatchesZeroAddress_thenReturnsTrue() throws UnknownHostException {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;
        final InetAddress zeroAddress = InetAddress.getByName("::");

        // When
        final boolean result = filter.matches(zeroAddress);

        // Then
        assertTrue("IPV6 filter should match ::", result);
        assertTrue("Address should be instance of Inet6Address", zeroAddress instanceof Inet6Address);
    }

    // ========== Null Safety Tests ==========

    @Test
    public void whenIPV4FilterMatchesNullAddress_thenReturnsFalse() {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4;

        // When
        final boolean result = filter.matches(null);

        // Then
        assertFalse("IPV4 filter should return false for null address", result);
    }

    @Test
    public void whenIPV6FilterMatchesNullAddress_thenReturnsFalse() {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV6;

        // When
        final boolean result = filter.matches(null);

        // Then
        assertFalse("IPV6 filter should return false for null address", result);
    }

    @Test
    public void whenIPV4AndIPV6FilterMatchesNullAddress_thenReturnsFalse() {
        // Given
        final IPVersionFilter filter = IPVersionFilter.IPV4_AND_IPV6;

        // When
        final boolean result = filter.matches(null);

        // Then
        assertFalse("IPV4_AND_IPV6 filter should return false for null address", result);
    }

    /**
     * Helper method to check if an array contains a specific enum value.
     */
    private boolean containsValue(final IPVersionFilter[] values, final IPVersionFilter target) {
        for (final IPVersionFilter value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
