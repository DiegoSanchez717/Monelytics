package dev.monelytics;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Replays a body already bounded by RequestContextFilter for the synchronous REST controllers. */
final class BoundedJsonRequest extends HttpServletRequestWrapper {
  private final byte[] body;

  BoundedJsonRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  @Override
  public int getContentLength() {
    return body.length;
  }

  @Override
  public long getContentLengthLong() {
    return body.length;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream source = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return source.read();
      }

      @Override
      public int read(byte[] bytes, int offset, int length) {
        return source.read(bytes, offset, length);
      }

      @Override
      public boolean isFinished() {
        return source.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {
        throw new IllegalStateException(
            "Asynchronous request body reads are not used by this API.");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    Charset charset =
        getCharacterEncoding() == null
            ? StandardCharsets.UTF_8
            : Charset.forName(getCharacterEncoding());
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }
}
