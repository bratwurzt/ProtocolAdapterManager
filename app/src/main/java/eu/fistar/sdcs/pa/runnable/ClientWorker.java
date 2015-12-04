package eu.fistar.sdcs.pa.runnable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * @author DusanM
 */
public abstract class ClientWorker implements Runnable
{
  protected SSLSocket m_socket;

  protected SSLSocket createSslSocket(KeyStore keystore, int serverPort)
      throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException
  {
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
    trustManagerFactory.init(keystore);
    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
    kmf.init(keystore, "klient1".toCharArray());
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
    SSLSocketFactory socketFactory = sc.getSocketFactory();
    return getSslSocket(serverPort, socketFactory);
  }

  private SSLSocket getSslSocket(int serverPort, SSLSocketFactory socketFactory) throws IOException
  {
    int localPort = 49152;
    Enumeration e = NetworkInterface.getNetworkInterfaces();
    InetAddress localInetAddress = InetAddress.getByName("localhost");
    outerLoop:
    while (e.hasMoreElements())
    {
      NetworkInterface n = (NetworkInterface)e.nextElement();
      Enumeration ee = n.getInetAddresses();
      while (ee.hasMoreElements())
      {
        InetAddress i = (InetAddress)ee.nextElement();

        if (i.isSiteLocalAddress())
        {
          localInetAddress = i;
          break outerLoop;
        }
      }
    }
    return (SSLSocket)socketFactory.createSocket(InetAddress.getByName("188.230.143.188"), serverPort, localInetAddress, localPort);
  }
}
