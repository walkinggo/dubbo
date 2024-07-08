/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.xds.protocol.impl;

import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.xds.AdsObserver;
import org.apache.dubbo.xds.listener.LdsListener;
import org.apache.dubbo.xds.protocol.AbstractProtocol;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.Rds;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.REGISTRY_ERROR_RESPONSE_XDS;

public class LdsProtocol extends AbstractProtocol<Listener> {

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(LdsProtocol.class);

    public LdsProtocol(AdsObserver adsObserver, Node node, int checkInterval, ApplicationModel applicationModel) {
        super(adsObserver, node, checkInterval, applicationModel);
        List<LdsListener> ldsListeners =
                applicationModel.getExtensionLoader(LdsListener.class).getActivateExtensions();
        ldsListeners.forEach(this::registerListen);
    }

    @Override
    public String getTypeUrl() {
        return "type.googleapis.com/envoy.config.listener.v3.Listener";
    }

    public void subscribeListeners() {
        subscribeResource(null);
    }

    @Override
    protected Map<String, Listener> decodeDiscoveryResponse(DiscoveryResponse response) {
        if (getTypeUrl().equals(response.getTypeUrl())) {
            return response.getResourcesList().stream()
                    .map(LdsProtocol::unpackListener)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toConcurrentMap(Listener::getName, Function.identity()));
        }
        return Collections.emptyMap();
    }

    private Set<String> decodeResourceToListener(Listener resource) {
        return resource.getFilterChainsList().stream()
                .flatMap(e -> e.getFiltersList().stream())
                .map(Filter::getTypedConfig)
                .map(LdsProtocol::unpackHttpConnectionManager)
                .filter(Objects::nonNull)
                .map(HttpConnectionManager::getRds)
                .map(Rds::getRouteConfigName)
                .collect(Collectors.toSet());
    }

    private static Listener unpackListener(Any any) {
        try {
            return any.unpack(Listener.class);
        } catch (InvalidProtocolBufferException e) {
            logger.error(REGISTRY_ERROR_RESPONSE_XDS, "", "", "Error occur when decode xDS response.", e);
            return null;
        }
    }

    private static HttpConnectionManager unpackHttpConnectionManager(Any any) {
        try {
            if (!any.is(HttpConnectionManager.class)) {
                return null;
            }
            return any.unpack(HttpConnectionManager.class);
        } catch (InvalidProtocolBufferException e) {
            logger.error(REGISTRY_ERROR_RESPONSE_XDS, "", "", "Error occur when decode xDS response.", e);
            return null;
        }
    }
}