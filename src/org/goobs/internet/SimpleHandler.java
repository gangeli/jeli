package org.goobs.internet;

import com.sun.net.httpserver.Headers;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public abstract class SimpleHandler implements WebServerHandler {
  @Override
  public void setHeaders(Headers responseHeaders) { }
}
