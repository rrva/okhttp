/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.testing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An event listener that enforces valid state transitions. For example, it is invalid to have
 * events after the {@link #callEnd} event.
 */
public final class StateMachineEventListener extends EventListener {
  public static final Factory FACTORY = call -> new StateMachineEventListener();

  enum CallState {
    NEW,
    STARTED,
    DNS_RESOLVING,
    DNS_RESOLVED,
    CONNECTING,
    SECURE_CONNECTING,
    SECURE_CONNECTED,
    CONNECTED,
    CONNECT_FAILED,
    CONNECTION_HELD,
    CONNECTION_RELEASED,
    ENDED,
    FAILED,
  }

  enum MessageState {
    READY,
    HEADERS_TRANSMITTING,
    HEADERS_TRANSMITTED,
    BODY_TRANSMITTING,
    BODY_TRANSMITTED,
    DONE
  }

  private CallState callState = CallState.NEW;
  private MessageState requestState = MessageState.READY;
  private MessageState responseState = MessageState.READY;

  @Override public void callStart(Call call) {
    requireCallState(CallState.NEW);
    callState = CallState.STARTED;
  }

  @Override public void dnsStart(Call call, String domainName) {
    requireCallState(CallState.STARTED, CallState.DNS_RESOLVED, CallState.CONNECT_FAILED,
        CallState.CONNECTION_RELEASED);
    callState = CallState.DNS_RESOLVING;
  }

  @Override public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
    requireCallState(CallState.DNS_RESOLVING);
    callState = CallState.DNS_RESOLVED;
  }

  @Override public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
    requireCallState(CallState.STARTED, CallState.DNS_RESOLVED, CallState.CONNECT_FAILED,
        CallState.CONNECTION_RELEASED, CallState.CONNECTED);
    callState = CallState.CONNECTING;
  }

  @Override public void secureConnectStart(Call call) {
    requireCallState(CallState.CONNECTING);
    callState = CallState.SECURE_CONNECTING;
  }

  @Override public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
    requireCallState(CallState.SECURE_CONNECTING);
    callState = CallState.SECURE_CONNECTED;
  }

  @Override public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy,
      @Nullable Protocol protocol) {
    requireCallState(CallState.CONNECTING, CallState.SECURE_CONNECTED);
    callState = CallState.CONNECTED;
  }

  @Override public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy,
      @Nullable Protocol protocol, IOException ioe) {
    requireCallState(CallState.CONNECTING, CallState.SECURE_CONNECTING);
    callState = CallState.CONNECT_FAILED;
  }

  @Override public void connectionAcquired(Call call, Connection connection) {
    requireCallState(CallState.STARTED, CallState.DNS_RESOLVED, CallState.CONNECTED,
        CallState.CONNECTION_RELEASED);
    callState = CallState.CONNECTION_HELD;
  }

  @Override public void connectionReleased(Call call, Connection connection) {
    requireCallState(CallState.CONNECTION_HELD);
    callState = CallState.CONNECTION_RELEASED;
    requestState = MessageState.READY;
    responseState = MessageState.READY;
  }

  @Override public void requestHeadersStart(Call call) {
    requireCallState(CallState.CONNECTION_HELD);
    requireRequestState(MessageState.READY, MessageState.HEADERS_TRANSMITTED,
        MessageState.BODY_TRANSMITTED);
    requestState = MessageState.HEADERS_TRANSMITTING;
  }

  @Override public void requestHeadersEnd(Call call, Request request) {
    requireCallState(CallState.CONNECTION_HELD);
    requireRequestState(MessageState.HEADERS_TRANSMITTING);
    requestState = MessageState.HEADERS_TRANSMITTED;
  }

  @Override public void requestBodyStart(Call call) {
    requireCallState(CallState.CONNECTION_HELD);
    requireRequestState(MessageState.HEADERS_TRANSMITTED);
    requestState = MessageState.BODY_TRANSMITTING;
  }

  @Override public void requestBodyEnd(Call call, long byteCount) {
    requireCallState(CallState.CONNECTION_HELD);
    requireRequestState(MessageState.BODY_TRANSMITTING);
    requestState = MessageState.BODY_TRANSMITTED;
  }

  @Override public void responseHeadersStart(Call call) {
    requireCallState(CallState.CONNECTION_HELD);
    requireResponseState(MessageState.READY, MessageState.HEADERS_TRANSMITTED,
        MessageState.BODY_TRANSMITTED);
    responseState = MessageState.HEADERS_TRANSMITTING;
  }

  @Override public void responseHeadersEnd(Call call, Response response) {
    requireCallState(CallState.CONNECTION_HELD);
    requireResponseState(MessageState.HEADERS_TRANSMITTING);
    responseState = MessageState.HEADERS_TRANSMITTED;
  }

  @Override public void responseBodyStart(Call call) {
    requireCallState(CallState.CONNECTION_HELD);
    requireResponseState(MessageState.HEADERS_TRANSMITTED);
    responseState = MessageState.BODY_TRANSMITTING;
  }

  @Override public void responseBodyEnd(Call call, long byteCount) {
    requireCallState(CallState.CONNECTION_HELD);
    requireResponseState(MessageState.BODY_TRANSMITTING);
    responseState = MessageState.BODY_TRANSMITTED;
  }

  @Override public void callEnd(Call call) {
    requireCallState(CallState.CONNECTION_RELEASED, CallState.STARTED);
    callState = CallState.ENDED;
  }

  @Override public void callFailed(Call call, IOException ioe) {
    requireCallState(CallState.STARTED, CallState.DNS_RESOLVED, CallState.DNS_RESOLVING,
        CallState.CONNECTED, CallState.CONNECT_FAILED, CallState.CONNECTION_RELEASED);
    callState = CallState.FAILED;
  }

  private void requireCallState(CallState... expected) {
    requireState(callState, expected);
  }

  private void requireRequestState(MessageState... expected) {
    requireState(requestState, expected);
  }

  private void requireResponseState(MessageState... expected) {
    requireState(responseState, expected);
  }

  private <T> void requireState(T actual, T... expected) {
    List<T> expectedList = Arrays.asList(expected);
    if (!expectedList.contains(actual)) {
      throw new IllegalStateException("expected " + actual + " to be in " + expectedList);
    }
  }
}
