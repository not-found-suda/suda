package com.ssafy.backend.domain.comms.service;

import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.NestConfig;
import com.nbp.cdncp.nest.grpc.proto.v1.NestData;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.NestServiceGrpc;
import com.nbp.cdncp.nest.grpc.proto.v1.RequestType;
import com.ssafy.backend.domain.comms.config.ClovaSpeechProperties;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClovaSpeechStreamingClient {

  private static final Logger log = LoggerFactory.getLogger(ClovaSpeechStreamingClient.class);

  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final int CHUNK_SIZE_BYTES = 3_200;
  private static final int WAV_HEADER_SIZE_BYTES = 44;
  private static final long CHUNK_INTERVAL_MILLIS = 100;
  private static final long RESPONSE_TIMEOUT_SECONDS = 30;

  private final ClovaSpeechProperties properties;

  public ClovaSpeechStreamingClient(ClovaSpeechProperties properties) {
    this.properties = properties;
  }

  public List<String> transcribe(InputStream audioStream, boolean skipWavHeader)
      throws InterruptedException, IOException {
    if (!properties.hasSecretKey()) {
      throw new IllegalStateException("CLOVA Speech secret key is not configured.");
    }

    ManagedChannel channel =
        NettyChannelBuilder.forTarget(properties.endpoint()).useTransportSecurity().build();

    try {
      NestServiceGrpc.NestServiceStub client =
          NestServiceGrpc.newStub(
              ClientInterceptors.intercept(
                  channel, MetadataUtils.newAttachHeadersInterceptor(authorizationMetadata())));

      List<String> responses = new ArrayList<>();
      CountDownLatch finishLatch = new CountDownLatch(1);
      AtomicReference<Throwable> responseError = new AtomicReference<>();

      StreamObserver<NestRequest> requestObserver =
          client.recognize(createResponseObserver(responses, responseError, finishLatch));

      sendConfig(requestObserver);
      sendAudio(audioStream, skipWavHeader, requestObserver);
      requestObserver.onCompleted();

      if (!finishLatch.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("CLOVA Speech streaming response timed out.");
      }

      Throwable error = responseError.get();
      if (error != null) {
        throw new IllegalStateException("CLOVA Speech streaming response failed.", error);
      }

      return responses;

    } finally {
      channel.shutdownNow();
    }
  }

  public StreamingCall openStream(ResponseHandler responseHandler) {
    if (!properties.hasSecretKey()) {
      throw new IllegalStateException("CLOVA Speech secret key is not configured.");
    }

    ManagedChannel channel =
        NettyChannelBuilder.forTarget(properties.endpoint()).useTransportSecurity().build();

    NestServiceGrpc.NestServiceStub client =
        NestServiceGrpc.newStub(
            ClientInterceptors.intercept(
                channel, MetadataUtils.newAttachHeadersInterceptor(authorizationMetadata())));

    StreamingCall call = new StreamingCall(channel);

    StreamObserver<NestResponse> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(NestResponse response) {
            responseHandler.onResponse(response.getContents());
          }

          @Override
          public void onError(Throwable throwable) {
            call.markClosed();
            responseHandler.onError(throwable);
            channel.shutdownNow();
          }

          @Override
          public void onCompleted() {
            call.markClosed();
            responseHandler.onCompleted();
            channel.shutdownNow();
          }
        };

    call.bind(client.recognize(responseObserver));
    sendConfig(call.requestObserver());
    return call;
  }

  private Metadata authorizationMetadata() {
    Metadata metadata = new Metadata();
    metadata.put(AUTHORIZATION_HEADER, "Bearer " + properties.secretKey());
    return metadata;
  }

  private StreamObserver<NestResponse> createResponseObserver(
      List<String> responses, AtomicReference<Throwable> error, CountDownLatch finishLatch) {
    return new StreamObserver<>() {
      @Override
      public void onNext(NestResponse response) {
        responses.add(response.getContents());
        log.info("[CLOVA Speech PoC] response={}", response.getContents());
      }

      @Override
      public void onError(Throwable throwable) {
        error.set(throwable);
        finishLatch.countDown();
      }

      @Override
      public void onCompleted() {
        finishLatch.countDown();
      }
    };
  }

  private void sendConfig(StreamObserver<NestRequest> requestObserver) {
    String configJson = "{\"transcription\":{\"language\":\"" + properties.language() + "\"}}";
    NestRequest configRequest =
        NestRequest.newBuilder()
            .setType(RequestType.CONFIG)
            .setConfig(NestConfig.newBuilder().setConfig(configJson).build())
            .build();

    requestObserver.onNext(configRequest);
  }

  private void sendAudio(
      InputStream audioStream, boolean skipWavHeader, StreamObserver<NestRequest> requestObserver)
      throws IOException, InterruptedException {
    if (skipWavHeader) {
      skipFully(audioStream, WAV_HEADER_SIZE_BYTES);
    }

    byte[] buffer = new byte[CHUNK_SIZE_BYTES];
    int seqId = 1;
    byte[] pendingChunk = null;
    int pendingBytesRead = 0;
    while (true) {
      int bytesRead = audioStream.read(buffer);
      if (bytesRead == -1) {
        break;
      }
      if (bytesRead == 0) {
        continue;
      }

      if (pendingChunk != null) {
        requestObserver.onNext(audioRequest(pendingChunk, pendingBytesRead, seqId++, false));
        Thread.sleep(CHUNK_INTERVAL_MILLIS);
      }

      pendingChunk = buffer.clone();
      pendingBytesRead = bytesRead;
    }

    if (pendingChunk != null) {
      requestObserver.onNext(audioRequest(pendingChunk, pendingBytesRead, seqId, true));
    }
  }

  private NestRequest audioRequest(byte[] buffer, int bytesRead, int seqId, boolean epFlag) {
    String extraContents = "{\"seqId\":" + seqId + ",\"epFlag\":" + epFlag + "}";
    return NestRequest.newBuilder()
        .setType(RequestType.DATA)
        .setData(
            NestData.newBuilder()
                .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                .setExtraContents(extraContents)
                .build())
        .build();
  }

  public interface ResponseHandler {

    void onResponse(String contents);

    void onError(Throwable throwable);

    void onCompleted();
  }

  public class StreamingCall implements AutoCloseable {

    private final ManagedChannel channel;
    private final AtomicInteger seqId = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private StreamObserver<NestRequest> requestObserver;

    private StreamingCall(ManagedChannel channel) {
      this.channel = channel;
    }

    private void bind(StreamObserver<NestRequest> requestObserver) {
      this.requestObserver = requestObserver;
    }

    private StreamObserver<NestRequest> requestObserver() {
      return requestObserver;
    }

    public void sendAudio(byte[] audioBytes, boolean epFlag) {
      if (audioBytes == null || audioBytes.length == 0 || closed.get()) {
        return;
      }

      synchronized (this) {
        if (!closed.get()) {
          requestObserver.onNext(
              audioRequest(audioBytes, audioBytes.length, seqId.getAndIncrement(), epFlag));
        }
      }
    }

    public void complete() {
      synchronized (this) {
        if (closed.compareAndSet(false, true)) {
          requestObserver.onCompleted();
        }
      }
    }

    private void markClosed() {
      closed.set(true);
    }

    @Override
    public void close() {
      synchronized (this) {
        if (closed.compareAndSet(false, true)) {
          requestObserver.onCompleted();
        }
      }
      channel.shutdownNow();
    }
  }

  private void skipFully(InputStream inputStream, long byteCount) throws IOException {
    long remaining = byteCount;
    while (remaining > 0) {
      long skipped = inputStream.skip(remaining);
      if (skipped <= 0) {
        if (inputStream.read() == -1) {
          throw new IOException("Audio stream ended before WAV header could be skipped.");
        }
        skipped = 1;
      }
      remaining -= skipped;
    }
  }
}
