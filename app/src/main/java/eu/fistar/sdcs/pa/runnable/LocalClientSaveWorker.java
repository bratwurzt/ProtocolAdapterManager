package eu.fistar.sdcs.pa.runnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import android.content.Context;
import eu.fistar.sdcs.pa.ZephyrProtos;

/**
 * @author bratwurzt
 */
public class LocalClientSaveWorker extends ClientWorker
{
  private FileOutputStream m_fileOutputStream;
  private File m_observationsFile;

  public LocalClientSaveWorker(File observationsFile, ZephyrProtos.ObservationsPB observations)
  {
    super(observations);
    m_observationsFile = observationsFile;
  }

  @Override
  OutputStream getOutputStream() throws Exception
  {
    m_fileOutputStream = new FileOutputStream(m_observationsFile, true);
    return m_fileOutputStream;
  }

  @Override
  protected void write(OutputStream outputStream) throws Exception
  {
    m_observations.writeDelimitedTo(outputStream);
  }

  @Override
  protected void close() throws Exception
  {
    m_fileOutputStream.close();
  }
}
