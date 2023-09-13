/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.server.mvc.handler;

import java.net.URI;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

public class ProxyExchangeHandlerFunction implements HandlerFunction<ServerResponse> {

	private static final Log log = LogFactory.getLog(ProxyExchangeHandlerFunction.class);

	private final ProxyExchange proxyExchange;

	private final ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters;

	private final ObjectProvider<ResponseHttpHeadersFilter> responseHttpHeadersFilters;

	private final URIResolver uriResolver;

	public ProxyExchangeHandlerFunction(ProxyExchange proxyExchange,
			ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters,
			ObjectProvider<ResponseHttpHeadersFilter> responseHttpHeadersFilters) {
		this(proxyExchange, requestHttpHeadersFilters, responseHttpHeadersFilters,
				request -> (URI) request.attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR)
						.orElseThrow(() -> new IllegalStateException("No routeUri resolved")));
	}

	public ProxyExchangeHandlerFunction(ProxyExchange proxyExchange,
			ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters,
			ObjectProvider<ResponseHttpHeadersFilter> responseHttpHeadersFilters, URIResolver uriResolver) {
		this.proxyExchange = proxyExchange;
		this.requestHttpHeadersFilters = requestHttpHeadersFilters;
		this.responseHttpHeadersFilters = responseHttpHeadersFilters;
		this.uriResolver = uriResolver;
	}

	@Override
	public ServerResponse handle(ServerRequest serverRequest) {
		URI uri = uriResolver.apply(serverRequest);
		boolean encoded = containsEncodedQuery(serverRequest.uri());
		// @formatter:off
		URI url = UriComponentsBuilder.fromUri(serverRequest.uri())
				// .uri(routeUri)
				.scheme(uri.getScheme())
				.host(uri.getHost())
				.port(uri.getPort())
				.queryParams(serverRequest.params())
				.build(encoded)
				.toUri();
		// @formatter:on

		// TODO: Streams.collect()?
		HttpHeaders filteredRequestHeaders = filterHeaders(
				this.requestHttpHeadersFilters.orderedStream().map(Function.identity()),
				serverRequest.headers().asHttpHeaders(), serverRequest);

		boolean preserveHost = (boolean) serverRequest.attributes()
				.getOrDefault(MvcUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, false);
		if (preserveHost) {
			filteredRequestHeaders.set(HttpHeaders.HOST, serverRequest.headers().firstHeader(HttpHeaders.HOST));
		}
		else {
			filteredRequestHeaders.remove(HttpHeaders.HOST);
		}

		// @formatter:off
		ProxyExchange.Request proxyRequest = proxyExchange.request(serverRequest).uri(url)
				.headers(filteredRequestHeaders)
				// TODO: allow injection of ResponseConsumer
				.responseConsumer((response, serverResponse) -> {
					HttpHeaders httpHeaders = filterHeaders(this.responseHttpHeadersFilters.orderedStream()
							.map(Function.identity()), response.getHeaders(), serverResponse);
					serverResponse.headers().putAll(httpHeaders);
				})
				.build();
		// @formatter:on
		return proxyExchange.exchange(proxyRequest);
	}

	private <TYPE> HttpHeaders filterHeaders(Stream<HttpHeadersFilter<TYPE>> filters, HttpHeaders original, TYPE type) {
		HttpHeaders filtered = original;
		for (var filter : filters.toList()) {
			filtered = filter.apply(filtered, type);
		}
		return filtered;
	}

	private static boolean containsEncodedQuery(URI uri) {
		boolean encoded = (uri.getRawQuery() != null && uri.getRawQuery().contains("%"))
				|| (uri.getRawPath() != null && uri.getRawPath().contains("%"));

		// Verify if it is really fully encoded. Treat partial encoded as unencoded.
		if (encoded) {
			try {
				UriComponentsBuilder.fromUri(uri).build(true);
				return true;
			}
			catch (IllegalArgumentException ignored) {
				if (log.isTraceEnabled()) {
					log.trace("Error in containsEncodedParts", ignored);
				}
			}

			return false;
		}

		return encoded;
	}

	public interface URIResolver extends Function<ServerRequest, URI> {

	}

}
