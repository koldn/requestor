/*
 * Copyright 2014 Danilo Reinert
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reinert.requestor;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.reinert.requestor.serialization.DeserializationContext;
import io.reinert.requestor.serialization.Deserializer;
import io.reinert.requestor.serialization.SerializationException;
import io.reinert.requestor.serialization.Serializer;

/**
 * Responsible for performing managed de/serialization.
 *
 * @author Danilo Reinert
 */
class SerializationEngine {

    private static Logger logger = Logger.getLogger(SerializationEngine.class.getName());

    private final SerdesManager serdesManager;
    private final ProviderManager providerManager;

    public SerializationEngine(SerdesManager serdesManager, ProviderManager providerManager) {
        this.serdesManager = serdesManager;
        this.providerManager = providerManager;
    }

    public <T, C extends Collection> DeserializedResponse<Collection<T>> deserializeResponse(Request request,
                                                                                            SerializedResponse response,
                                                                                            Class<T> type,
                                                                                            Class<C> containerType) {
        String responseContentType = getResponseContentType(request, response);
        final Deserializer<T> deserializer = serdesManager.getDeserializer(type, responseContentType);
        checkDeserializerNotNull(response, type, deserializer);
        final DeserializationContext context = new HttpDeserializationContext(request, response, type, providerManager);
        @SuppressWarnings("unchecked")
        Collection<T> result = deserializer.deserialize(containerType, response.getPayload().isString(), context);
        return getDeserializedResponse(response, result);
    }

    public <T> DeserializedResponse<T> deserializeResponse(Request request, SerializedResponse response,
                                                           Class<T> type) {
        String responseContentType = getResponseContentType(request, response);
        final Deserializer<T> deserializer = serdesManager.getDeserializer(type, responseContentType);
        checkDeserializerNotNull(response, type, deserializer);
        final DeserializationContext context = new HttpDeserializationContext(request, response, type, providerManager);
        T result = deserializer.deserialize(response.getPayload().isString(), context);
        return getDeserializedResponse(response, result);
    }

    @SuppressWarnings("unchecked")
    public SerializedRequestImpl serializeRequest(Request request) {
        Object payload = request.getPayload();
        String body = null;
        if (payload != null) {
            if (payload instanceof Collection) {
                Collection c = (Collection) payload;
                final Iterator iterator = c.iterator();
                Object item = null;
                while (iterator.hasNext() && item == null) {
                    item = iterator.next();
                }
                if (item == null) {
                    /* FIXME: This is forcing empty collections responses to be a empty json array.
                        It will cause error for serializers expecting other content-type (e.g. XML).
                       TODO: Create some EmptyCollectionSerializerManager for serialization of empty collections
                        by content-type. */
                    body = "[]";
                } else {
                    Serializer<?> serializer = serdesManager.getSerializer(item.getClass(), request.getContentType());
                    checkSerializerNotNull(request, item.getClass(), serializer);
                    body = serializer.serialize(c, new HttpSerializationContext(request));
                }
            } else {
                Serializer<Object> serializer = (Serializer<Object>) serdesManager.getSerializer(payload.getClass(),
                        request.getContentType());
                checkSerializerNotNull(request, payload.getClass(), serializer);
                body = serializer.serialize(payload, new HttpSerializationContext(request));
            }
        }
        return new SerializedRequestImpl(request, new Payload(body));
    }

    private String getResponseContentType(Request request, SerializedResponse response) {
        String responseContentType = response.getContentType();
        if (responseContentType == null || responseContentType.isEmpty()) {
            responseContentType = "*/*";
            logger.log(Level.INFO, "Response with no 'Content-Type' header received from '" + request.getUrl()
                    + "'. The content-type value has been automatically set to '*/*' to match deserializers.");
        }
        return responseContentType;
    }

    private <T> DeserializedResponse<T> getDeserializedResponse(SerializedResponse response, T result) {
        return new DeserializedResponse<T>(response.getHeaders(), response.getStatusCode(), response.getStatusText(),
                response.getResponseType(), result);
    }

    private void checkDeserializerNotNull(SerializedResponse response, Class<?> type, Deserializer<?> deserializer) {
        if (deserializer == null)
            throw new SerializationException("Could not find Deserializer for class '" + type.getName() + "' and " +
                    "media-type '" + response.getContentType() + "'.");
    }

    private void checkSerializerNotNull(Request request, Class<?> type, Serializer<?> serializer) {
        if (serializer == null)
            throw new SerializationException("Could not find Serializer for class '" + type.getName() + "' and " +
                    "media-type '" + request.getContentType() + "'.");
    }
}