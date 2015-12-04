package eu.fistar.sdcs.pa.runnable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import eu.fistar.sdcs.pa.ZephyrProtos;

/**
 * @author DusanM
 */
public class ClientSaveWorker extends ClientWorker
{
  private ZephyrProtos.ObservationsPB m_observations;
  private KeyStore m_keystore;

  public ClientSaveWorker(ZephyrProtos.ObservationsPB observations, KeyStore keystore)
  {
    m_observations = observations;
    m_keystore = keystore;
  }

  @Override
  public void run()
  {
    try
    {
      m_socket = createSslSocket(m_keystore, 8099);
      try (OutputStream outputStream = m_socket.getOutputStream())
      {
        m_observations.writeTo(outputStream);
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        m_socket.close();
      }
    }
    catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException e)
    {
      e.printStackTrace();
    }
  }
}
