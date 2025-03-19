package com.amazonaws.kinesisvideo.http;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * A DNS resolver that applies an IP version filter when resolving hostnames.
 * This ensures that only IP addresses matching the specified filter are returned.
 */
public class KvsFilteredDnsResolver implements DnsResolver, com.amazonaws.DnsResolver {

    private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    /** The IP version filter used for filtering resolved addresses. */
    @Nonnull
    private final IPVersionFilter ipVersionFilter;

    /**
     * Constructs a new {@code KvsFilteredDnsResolver} with the specified IP version filter.
     *
     * @param ipVersionFilter the IP version filter to apply; if {@code null}, defaults to {@link IPVersionFilter#IPV4_AND_IPV6}.
     */
    public KvsFilteredDnsResolver(@Nullable final IPVersionFilter ipVersionFilter) {
        super();

        if (ipVersionFilter != null) {
            this.ipVersionFilter = ipVersionFilter;
        } else {
            // No filter, defaulting to allow both IPv4 and IPv6 (no filter)
            this.ipVersionFilter = IPVersionFilter.IPV4_AND_IPV6;
            log.debug("Defaulting to filter: {}", this.ipVersionFilter);
        }
    }

    /**
     * Resolves the given hostname and filters the results based on the configured {@link IPVersionFilter}.
     *
     * @param host the hostname to resolve
     * @return an array of {@link InetAddress} containing only addresses that match the IP version filter
     * @throws UnknownHostException if no matching addresses are found or if the hostname cannot be resolved
     */
    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        log.debug("Resolving {}", host);
        final InetAddress[] resolvedAddresses = Arrays.stream(SystemDefaultDnsResolver.INSTANCE.resolve(host))
                .peek(inetAddr -> log.debug("Resolved IP: {}", inetAddr))
                .filter(this.ipVersionFilter::matches)
                .toArray(InetAddress[]::new);

        if (resolvedAddresses.length == 0) {
            throw new UnknownHostException("Not able to resolve any " + ipVersionFilter + " addresses for " + host + "!");
        }

        log.debug("{} filtered IP's: {}", ipVersionFilter, Arrays.toString(resolvedAddresses));

        // Will always return an array with at least 1 element
        return resolvedAddresses;
    }
}
