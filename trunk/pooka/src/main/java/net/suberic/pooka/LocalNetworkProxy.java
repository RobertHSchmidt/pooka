package net.suberic.pooka;

import java.net.*;
import java.util.*;

public class LocalNetworkProxy extends ProxySelector {
  static boolean enabled = false;
  private static ProxySelector defaultProxySelector = null;
  static boolean allowHttp = false;
  public static synchronized void enable() {
    if (! enabled) {
      defaultProxySelector = ProxySelector.getDefault();
      ProxySelector.setDefault(new LocalNetworkProxy());
      enabled = true;
    }
  }

  public List<Proxy> select(URI u) {
    if ((! allowHttp) && u != null && ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))) {
      return new ArrayList<Proxy>();
    } else {
      return defaultProxySelector.select(u);
    }
  }
  public void connectFailed(URI u, SocketAddress a, java.io.IOException e) {
    defaultProxySelector.connectFailed(u, a, e);
  }

  public static void setAllowHttp(boolean newvalue) {
    allowHttp = newvalue;
  }
  public static boolean getAllowHttp() {
    return allowHttp;
  }
}
