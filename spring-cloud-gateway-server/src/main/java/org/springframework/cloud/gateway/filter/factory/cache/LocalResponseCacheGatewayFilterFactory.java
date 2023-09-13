/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * {@link org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory} of
 * {@link ResponseCacheGatewayFilter}.
 *
 * By default, a global cache (defined as properties in the application) is used. For
 * specific route configuration, parameters can be added following
 * {@link RouteCacheConfiguration} class.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
@ConditionalOnProperty(value = "spring.cloud.gateway.filter.local-response-cache.enabled", havingValue = "true")
public class LocalResponseCacheGatewayFilterFactory
		extends AbstractGatewayFilterFactory<LocalResponseCacheGatewayFilterFactory.RouteCacheConfiguration> {

	/**
	 * Exchange attribute name to track if the request has been already process by cache
	 * at route filter level.
	 */
	public static final String LOCAL_RESPONSE_CACHE_FILTER_APPLIED = "LocalResponseCacheGatewayFilter-Applied";

	private ResponseCacheManagerFactory cacheManagerFactory;

	private Duration defaultTimeToLive;

	private DataSize defaultSize;

	public LocalResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory cacheManagerFactory,
			Duration defaultTimeToLive) {
		this(cacheManagerFactory, defaultTimeToLive, null);
	}

	public LocalResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory cacheManagerFactory,
			Duration defaultTimeToLive, DataSize defaultSize) {
		super(RouteCacheConfiguration.class);
		this.cacheManagerFactory = cacheManagerFactory;
		this.defaultTimeToLive = defaultTimeToLive;
		this.defaultSize = defaultSize;
	}

	@Override
	public GatewayFilter apply(RouteCacheConfiguration config) {
		LocalResponseCacheProperties cacheProperties = mapRouteCacheConfig(config);

		Cache routeCache = LocalResponseCacheAutoConfiguration.createGatewayCacheManager(cacheProperties)
				.getCache(config.getRouteId() + "-cache");
		return new ResponseCacheGatewayFilter(cacheManagerFactory.create(routeCache, cacheProperties.getTimeToLive()));

	}

	private LocalResponseCacheProperties mapRouteCacheConfig(RouteCacheConfiguration config) {
		Duration timeToLive = config.getTimeToLive() != null ? config.getTimeToLive() : defaultTimeToLive;
		DataSize size = config.getSize() != null ? config.getSize() : defaultSize;

		LocalResponseCacheProperties responseCacheProperties = new LocalResponseCacheProperties();
		responseCacheProperties.setTimeToLive(timeToLive);
		responseCacheProperties.setSize(size);
		return responseCacheProperties;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("timeToLive", "size");
	}

	@Validated
	public static class RouteCacheConfiguration implements HasRouteId {

		private DataSize size;

		private Duration timeToLive;

		private String routeId;

		public DataSize getSize() {
			return size;
		}

		public RouteCacheConfiguration setSize(DataSize size) {
			this.size = size;
			return this;
		}

		public Duration getTimeToLive() {
			return timeToLive;
		}

		public RouteCacheConfiguration setTimeToLive(Duration timeToLive) {
			this.timeToLive = timeToLive;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String getRouteId() {
			return this.routeId;
		}

	}

}
