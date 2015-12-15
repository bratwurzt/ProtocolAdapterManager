package eu.fistar.sdcs.pa.runnable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import javax.net.SocketFactory;

import android.annotation.TargetApi;
import android.os.Build;
import eu.fistar.sdcs.pa.ZephyrProtos;
import eu.fistar.sdcs.pa.common.Observation;
import eu.fistar.sdcs.pa.helper.XORShiftRandom;

/**
 * @author bratwurzt
 */
public class RemoteClientSaveWorker implements Runnable
{
  private Socket m_socket;
  private String m_ipAddress;
  private Integer m_port;
  protected final BlockingQueue<Observation> m_observations;
  protected OutputStream m_outputStream;
  protected ZephyrProtos.ObservationsPB m_obss;

  public RemoteClientSaveWorker(BlockingQueue<Observation> observations, ZephyrProtos.ObservationsPB obss, Socket socket, String ipAddress, Integer port)
  {
    m_observations = observations;
    m_obss = obss;
    m_socket = socket;
    m_ipAddress = ipAddress;
    m_port = port;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public void run()
  {
    try
    {
      List<Observation> tempList = new ArrayList<>();
      try (OutputStream outputStream = getOutputStream())
      {
        ZephyrProtos.ObservationsPB.Builder builder = ZephyrProtos.ObservationsPB.newBuilder();
        Observation obs;
        synchronized (m_observations)
        {
          while ((obs = m_observations.poll()) != null)
          {
            tempList.add(obs);
            builder.addObservations(
                ZephyrProtos.ObservationPB.newBuilder()
                    .setName(obs.getPropertyName())
                    .setUnit(obs.getMeasurementUnit())
                    .setTime(obs.getPhenomenonTime())
                    .setDuration((int)obs.getDuration())
                    .addAllValues(obs.getValues())
            );
          }
        }
        if (m_obss != null && m_obss.getObservationsCount() > 0)
        {
          builder.addAllObservations(m_obss.getObservationsList());
        }
        builder.build().writeTo(outputStream);
      }
      catch (Exception e)
      {
        synchronized (m_observations)
        {
          m_observations.addAll(tempList);
        }
      }
      finally
      {
        if (m_outputStream != null)
        {
          m_outputStream.close();
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public OutputStream getOutputStream()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException
  {
    if (m_outputStream == null)
    {
      if (m_socket == null/* || !m_socket.isConnected() || m_socket.isClosed()*/)
      {
        try
        {
          m_socket = createSocket(m_port, m_ipAddress);
        }
        catch (IOException e)
        {
          e.printStackTrace();
        }
      }

      m_outputStream = m_socket.getOutputStream();
    }
    return m_outputStream;
  }

  private Socket createSocket(int serverPort, String serverIPAddress) throws IOException
  {
    XORShiftRandom xorShiftRandom = new XORShiftRandom();
    int localPort = xorShiftRandom.nextInt(49152, 55535);
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
    return SocketFactory.getDefault().createSocket(InetAddress.getByName(serverIPAddress), serverPort, localInetAddress, localPort);
  }
}
