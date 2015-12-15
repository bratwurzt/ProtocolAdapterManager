package eu.fistar.sdcs.pa.runnable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import eu.fistar.sdcs.pa.MainActivity;
import eu.fistar.sdcs.pa.ZephyrProtos;
import eu.fistar.sdcs.pa.common.Observation;

/**
 * @author DusanM
 */
public class LocalClientSaveWorker implements Runnable
{
  protected final BlockingQueue<Observation> m_observations;
  protected final File m_observationsFile;
  protected FileOutputStream m_outputStream;

  public LocalClientSaveWorker(File observationsFile, BlockingQueue<Observation> observations)
  {
    m_observationsFile = observationsFile;
    m_observations = observations;
  }

  @Override
  public void run()
  {
    try
    {
      List<Observation> tempList = new ArrayList<>();
      synchronized (MainActivity.FILE_LOCK)
      {
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
          builder.build().writeDelimitedTo(outputStream);
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
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public FileOutputStream getOutputStream() throws FileNotFoundException
  {
    if (m_outputStream == null)
    {
      m_outputStream = new FileOutputStream(m_observationsFile, true);
    }
    return m_outputStream;
  }
}
