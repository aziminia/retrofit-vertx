package io.vertx.ext.retrofit;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxRetrofit implements Call.Factory {

  private final HttpClient client;

  public VertxRetrofit(HttpClient client) {
    this.client = client;
  }

  private class VertxCall implements okhttp3.Call {

    private final Request request;
    private final AtomicBoolean executed = new AtomicBoolean();

    VertxCall(Request request) {
      this.request = request;
    }

    @Override
    public Request request() {
      return request;
    }

    @Override
    public Response execute() throws IOException {
      CompletableFuture<Response> future = new CompletableFuture<>();
      enqueue(new Callback() {

        @Override
        public void onResponse(okhttp3.Call call, Response response) throws IOException {
          future.complete(response);
        }

        @Override
        public void onFailure(okhttp3.Call call, IOException e) {
          future.completeExceptionally(e);
        }
      });
      try {
        return future.get(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

    @Override
    public void enqueue(Callback callback) {
      if (executed.compareAndSet(false, true)) {
        HttpMethod method = HttpMethod.valueOf(request.method());
        client.requestAbs(method, request.url().toString(), resp -> {
          resp.bodyHandler(body -> {
            try {
              Response.Builder builder = new Response.Builder();
              builder.protocol(Protocol.HTTP_1_1);
              builder.request(request);
              builder.code(resp.statusCode());
              for (Map.Entry<String, String> header : resp.headers()) {
                builder.addHeader(header.getKey(), header.getValue());
              }
              String mediaTypeHeader = resp.getHeader("Content-Type");
              MediaType mediaType =  mediaTypeHeader != null ? MediaType.parse(mediaTypeHeader) : null;
              builder.body(ResponseBody.create(mediaType, body.getBytes()));
              callback.onResponse(this, builder.build());
            } catch (Exception e) {
              callback.onFailure(this, new IOException(e));
            }
          });
        }).end();
      } else {
        callback.onFailure(this, new IOException("Already executed"));
      }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isExecuted() {
      return executed.get();
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public Call clone() {
      return new VertxCall(request);
    }
  }

  @Override
  public okhttp3.Call newCall(Request request) {
    return new VertxCall(request);
  }
}
