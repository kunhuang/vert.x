/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.examples.wsperf;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.deploy.Verticle;

import java.util.LinkedList;
import java.util.Queue;

public class PerfClient extends Verticle {

  private HttpClient client;

  // Number of connections to create
  private static final int CONNS = 10;

  private int statsCount;

  private EventBus eb;

  private static final int STR_LENGTH = 512;

  private static final int STATS_BATCH = 1024 * 1024;

  private static final int BUFF_SIZE = 16 * 1024;

  private String message;

  private Buffer buff;

  public PerfClient() {
    StringBuilder sb = new StringBuilder(STR_LENGTH);
    for (int i = 0; i < STR_LENGTH; i++) {
      sb.append('X');
    }
    message = sb.toString();
    buff = new Buffer(message);
  }

  int connectCount;

  Queue<WebSocket> websockets = new LinkedList<>();

  private void connect(final int count) {
    client.connectWebsocket("/someuri", new Handler<WebSocket>() {
      public void handle(final WebSocket ws) {
        connectCount++;
        System.out.println("ws connected: " + connectCount);

        ws.setWriteQueueMaxSize(BUFF_SIZE);
        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            int len = data.length();
            statsCount += len;
            if (statsCount > STATS_BATCH) {
              eb.send("rate-counter", statsCount);
              statsCount = 0;
            }
          }
        });

        websockets.add(ws);
        if (connectCount == CONNS) {
          startWebSocket();
        }
      }
    });
    if (count + 1 < CONNS) {
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          connect(count + 1);
        }
      });
    }
  }

  private void startWebSocket() {
    WebSocket ws = websockets.poll();
    writeWebSocket(ws);
    if (!websockets.isEmpty()) {
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          startWebSocket();
        }
      });
    }

  }

  public void start() {
    System.out.println("Starting perf client");
    eb = vertx.eventBus();
    client = vertx.createHttpClient().setPort(8080).setHost("localhost").setMaxPoolSize(CONNS);
    client.setReceiveBufferSize(BUFF_SIZE);
    client.setSendBufferSize(BUFF_SIZE);
    connect(0);
  }

  private void writeWebSocket(final WebSocket ws) {
    if (!ws.writeQueueFull()) {
      ws.writeTextFrame(message);
      //ws.writeBinaryFrame(buff);
      //System.out.println("wrote buffer");
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          writeWebSocket(ws);
        }
      });
    } else {
      // Flow control
      ws.drainHandler(new SimpleHandler() {
        public void handle() {
          writeWebSocket(ws);
        }
      });
    }
  }

}
