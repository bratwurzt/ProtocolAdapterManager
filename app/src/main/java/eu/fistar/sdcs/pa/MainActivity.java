/**
 * Copyright (C) 2014 Consorzio Roma Ricerche All rights reserved This file is part of the Protocol Adapter software, available at
 * https://github.com/theIoTLab/ProtocolAdapter . The Protocol Adapter is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the License. This program is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public License along with this program.  If not, see http://opensource.org/licenses/LGPL-3.0
 * Contact Consorzio Roma Ricerche (protocoladapter@gmail.com)
 */

package eu.fistar.sdcs.pa;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import eu.fistar.sdcs.pa.common.Capabilities;
import eu.fistar.sdcs.pa.common.DeviceDescription;
import eu.fistar.sdcs.pa.common.IProtocolAdapter;
import eu.fistar.sdcs.pa.common.IProtocolAdapterListener;
import eu.fistar.sdcs.pa.common.Observation;
import eu.fistar.sdcs.pa.common.PAAndroidConstants;
import eu.fistar.sdcs.pa.dialogs.ConfigDaDialogFragment;
import eu.fistar.sdcs.pa.dialogs.ConnectDevDialogFragment;
import eu.fistar.sdcs.pa.dialogs.DisconnectDevDialogFragment;
import eu.fistar.sdcs.pa.dialogs.IPADialogListener;
import eu.fistar.sdcs.pa.dialogs.SendCommandDialogFragment;
import eu.fistar.sdcs.pa.dialogs.StartDaDialogFragment;
import eu.fistar.sdcs.pa.dialogs.StopDaDialogFragment;
import eu.fistar.sdcs.pa.runnable.LocalClientSaveWorker;
import eu.fistar.sdcs.pa.runnable.RemoteClientSaveWorker;

/**
 * This is just an example activity used to bind the Protocol Adapter directly in case it's not bounded elsewhere. Remember that you can bind the Protocol Adapter
 * from here but it should be directly bounded by the software that uses it e.g. by the Sensor Data Collection Service SE.
 *
 * @author Marcello Morena
 * @author Alexandru Serbanati
 */
public class MainActivity extends FragmentActivity implements IPADialogListener
{
  static
  {
    System.setProperty("ssl.TrustManagerFactory.algorithm", javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
  }

  private static final Pattern PARTIAl_IP_ADDRESS =
      Pattern.compile("^((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])\\.){0,3}((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])){0,1}$");
  public static int BATCH_TIME = 2000;
  private Long m_lastTime;
  private final SimpleDateFormat m_dateFormat = new SimpleDateFormat("yyyyMMddhhmm'.csv'");
  private static final String LOGTAG = "PA Activity";
  private IProtocolAdapter pa;
  final Object LOCK = new Object();
  protected ExecutorService m_queryThreadExecutor = Executors.newSingleThreadExecutor();

  private Handler mHandler = null;
  private LooperThread looperThread;
  private String format = m_dateFormat.format(new Date());
  private File m_observationsFile;
  public static final Object FILE_LOCK = new Object();
  private ConnectivityManager m_connManager;
  private KeyStore m_keystore;
  private PowerManager.WakeLock m_wakeLock;
  protected final BlockingQueue<Observation> m_observations = new LinkedBlockingQueue<>();
  private EditText m_ipAddressEditText, m_portEditText;

  private ServiceConnection serv = new ServiceConnection()
  {
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder)
    {
      // Get the output TextView
      ((TextView)findViewById(R.id.lblServiceFeedback)).setText(getText(R.string.lbl_feedback_started));

      // Disable the start button
      (findViewById(R.id.btnStartService)).setEnabled(false);

      // Enable the stop button
      (findViewById(R.id.btnStopService)).setEnabled(true);

      // Enable the stop button
      (findViewById(R.id.btnStopService)).setEnabled(true);

      // Enable the start DA button
      (findViewById(R.id.btnStartDA)).setEnabled(true);

      // Enable the stop DA button
      (findViewById(R.id.btnStopDA)).setEnabled(true);

      // Enable the config DA button
      (findViewById(R.id.btnConfigDA)).setEnabled(true);

      // Enable the connect device button
      (findViewById(R.id.btnConnectDevices)).setEnabled(true);

      // Enable the disconnect device button
      (findViewById(R.id.btnDisconnectDevices)).setEnabled(true);

      // Enable the send command button
      (findViewById(R.id.btnSendCommand)).setEnabled(true);

      // Start all the Device Adapters of the system
      pa = IProtocolAdapter.Stub.asInterface(iBinder);
      try
      {
        AssetManager assetMgr = getAssets();
        InputStream clientJksInputStream = assetMgr.open("client.bks");
        m_keystore = KeyStore.getInstance("BKS");
        m_keystore.load(clientJksInputStream, "klient1".toCharArray());

        looperThread = new LooperThread(m_keystore);
        pa.registerPAListener(looperThread);
        new Thread(looperThread).start();
        // Registering listener

        // Start all the Device Adapters of the system
        @SuppressWarnings("unchecked")
        Map<String, Capabilities> das = (Map<String, Capabilities>)pa.getAvailableDAs();

        updateLog("Starting all Device Adapters of the system");

        for (String tempDa : das.keySet())
        {
          pa.startDA(tempDa);
        }
      }
      catch (RemoteException e)
      {
        updateLog("Error contacting Protocol Adapter");
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
      catch (KeyStoreException e)
      {
        e.printStackTrace();
      }
      catch (CertificateException e)
      {
        e.printStackTrace();
      }
      catch (NoSuchAlgorithmException e)
      {
        e.printStackTrace();
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName)
    {
      updateLog("Protocol Adapter disconnected unexpectedly");
      mHandler.getLooper().quit();
    }
  };

  /**
   * Dummy implementation of the IProtocolAdapterListener interface which just prints on the logs all the data received by the Protocol Adapter.
   */

  class LooperThread extends IProtocolAdapterListener.Stub implements Runnable
  {
    private Socket m_socket;

    public LooperThread(KeyStore keystore)
    {
      m_keystore = keystore;
    }

    @Override
    public void run()
    {
      m_lastTime = System.currentTimeMillis();
      Looper.prepare();
      mHandler = new Handler();
      Looper.loop();
    }

    @Override
    public void registerDevice(DeviceDescription deviceDescription, String daId) throws RemoteException
    {
      updateLog("Device registered " + deviceDescription.getDeviceID() + ", handled by Device Adapter " + daId);
    }

    @Override
    public void pushData(final List<Observation> observations, DeviceDescription deviceDescription) throws RemoteException
    {
      for (Observation obs : observations)
      {
        String propertyName = obs.getPropertyName();
        if (!"galvanic skin response".equals(propertyName)
            && !"temperature".equals(propertyName)
            && !"zephyr sys chan".equals(propertyName)
            && !"user intf btn status".equals(propertyName)
            )
        {
          synchronized (m_observations)
          {
            m_observations.add(obs);
          }
        }
      }

      if (System.currentTimeMillis() - BATCH_TIME > m_lastTime)
      {
        saveData();
        m_lastTime = System.currentTimeMillis();
      }
    }

    private void saveData()
    {
      String typeName;
      try
      {
        NetworkInfo activeNetInfo = getConnManager().getActiveNetworkInfo();
        typeName = activeNetInfo == null ? null : activeNetInfo.getTypeName();
      }
      catch (Exception e)
      {
        Log.e(LOGTAG, e.getMessage());
        typeName = null;
      }

      if ("WIFI".equals(typeName))
      {
        ZephyrProtos.ObservationsPB obss = getFileObservations();
        if (obss != null && obss.getObservationsCount() > 0)
        {
          emptyObservationFile();
        }
        String ipAddress = m_ipAddressEditText.getText().toString();
        Integer port = Integer.parseInt(m_portEditText.getText().toString());
        m_queryThreadExecutor.execute(new RemoteClientSaveWorker(m_observations, obss, m_socket, ipAddress, port));
      }
      else if (m_observations.size() > 2000)
      {
        m_queryThreadExecutor.execute(new LocalClientSaveWorker(m_observationsFile, m_observations));
      }
    }

    private void emptyObservationFile()
    {
      String string1 = "";
      FileOutputStream fos;
      try
      {
        synchronized (FILE_LOCK)
        {
          fos = new FileOutputStream(m_observationsFile, false);
          FileWriter fWriter;

          try
          {
            fWriter = new FileWriter(fos.getFD());

            fWriter.write(string1);
            fWriter.flush();
            fWriter.close();
          }
          catch (Exception e)
          {
            e.printStackTrace();
          }
          finally
          {
            fos.getFD().sync();
            fos.close();
          }
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }

    private ZephyrProtos.ObservationsPB getFileObservations()
    {
      if (!m_observationsFile.exists())
      {
        return null;
      }
      ZephyrProtos.ObservationsPB observationsPB = null;
      try
      {
        synchronized (FILE_LOCK)
        {
          observationsPB = ZephyrProtos.ObservationsPB.parseDelimitedFrom(new FileInputStream(m_observationsFile));
        }
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
      return observationsPB;
    }

    @Override
    public void deregisterDevice(DeviceDescription deviceDescription) throws RemoteException
    {
      updateLog("Device deregistered " + deviceDescription.getDeviceID());
    }

    @Override
    public void registerDeviceProperties(DeviceDescription deviceDescription) throws RemoteException
    {
      updateLog("Device properties registered: " + deviceDescription.getDeviceID());
    }

    @Override
    public void deviceDisconnected(DeviceDescription deviceDescription) throws RemoteException
    {
      updateLog("Device disconnected: " + deviceDescription.getDeviceID());
    }

    @Override
    public void log(int logLevel, String daId, String message) throws RemoteException
    {
      updateLog("LOG! Level: " + logLevel + "; DA: " + daId + "; Message: " + message + ";");
    }

    @Override
    public void onDAConnected(String daId) throws RemoteException
    {
      updateLog("Device Adapter " + daId + " completed the initialization phase");
    }

  }

  private ConnectivityManager getConnManager()
  {
    if (m_connManager == null)
    {
      m_connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    return m_connManager;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    synchronized (FILE_LOCK)
    {
      m_observationsFile = createObsFile();
    }
    setContentView(R.layout.activity_main);
    PowerManager m_pwrManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
    m_wakeLock = m_pwrManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
    m_portEditText = (EditText)findViewById(R.id.port_edit_text);
    m_ipAddressEditText = (EditText)findViewById(R.id.ip_address_edit_text);
    m_ipAddressEditText.addTextChangedListener(new TextWatcher()
    {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count)
      {
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after)
      {
      }

      private String mPreviousText = "";

      @Override
      public void afterTextChanged(Editable s)
      {
        if (PARTIAl_IP_ADDRESS.matcher(s).matches())
        {
          mPreviousText = s.toString();
        }
        else
        {
          s.replace(0, s.length(), mPreviousText);
        }
      }
    });
  }

  private File createObsFile()
  {
    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
    path.mkdirs();
    return new File(path, "observations.dat");
  }

  public void startService(View v)
  {
    updateLog("Starting the Protocol Adapter");

    // Create the Intent to start the PA with
    Intent intent = new Intent().setComponent(new ComponentName(PAAndroidConstants.PA_PACKAGE, PAAndroidConstants.PA_ACTION));

    // Start the Protocol Adapter
    bindService(intent, serv, Context.BIND_AUTO_CREATE);
  }

  public void stopService(View v)
  {
    updateLog("Stopping the Protocol Adapter");

    // Unbind the service
    this.unbindService(serv);

    // Get the output TextView
    ((TextView)findViewById(R.id.lblServiceFeedback)).setText(getText(R.string.lbl_feedback_unknown));

    // Disable the start button
    (findViewById(R.id.btnStartService)).setEnabled(true);

    // Disable the stop button
    (findViewById(R.id.btnStopService)).setEnabled(false);

    // Disable the start DA button
    (findViewById(R.id.btnStartDA)).setEnabled(false);

    // Disable the stop DA button
    (findViewById(R.id.btnStopDA)).setEnabled(false);

    // Disable the config DA button
    (findViewById(R.id.btnConfigDA)).setEnabled(false);

    // Disable the connect device button
    (findViewById(R.id.btnConnectDevices)).setEnabled(false);

    // Disable the disconnect device button
    (findViewById(R.id.btnDisconnectDevices)).setEnabled(false);

    // Disable the send command button
    (findViewById(R.id.btnSendCommand)).setEnabled(false);
  }

  @Override
  public void connectDev(final String devId, String daId)
  {
    try
    {
      // Connect to the specified device
      updateLog("Connecting to device: " + devId);
      pa.forceConnectDev(devId, daId);
      final AsyncTaskRunner runner = new AsyncTaskRunner();
      mHandler.postDelayed(new Runnable()
      {
        @Override
        public void run()
        {
          runner.execute(devId);
        }
      }, 1000 * 3);
    }
    catch (RemoteException e)
    {
      updateLog("Error communicating wih the PA");
    }
    finally
    {
      synchronized (LOCK)
      {
        if (!m_wakeLock.isHeld())
        {
          m_wakeLock.acquire();
        }
      }
    }
  }

  @Override
  public void disconnectDev(String devId)
  {
    try
    {
      // Disconnect from the specified device
      updateLog("Disconnecting from device: " + devId);
      pa.disconnectDev(devId);
    }
    catch (RemoteException e)
    {
      updateLog("Error communicating wih the PA");
    }
    finally
    {
      synchronized (LOCK)
      {
        if (m_wakeLock.isHeld())
        {
          m_wakeLock.release();
        }
      }
    }
  }

  @Override
  public void sendCommand(String command, String parameter, String devId)
  {
    try
    {
      pa.execCommand(command, parameter != null ? parameter : "", devId);
    }
    catch (RemoteException e)
    {
      updateLog("Error executing command");
    }
  }

  @Override
  public void configDa(ComponentName comp)
  {
    Intent intent = new Intent().setComponent(comp);
    startActivity(intent);
  }

  @Override
  public void startDA(String daId)
  {
    try
    {
      updateLog("Starting the Device Adapter " + daId);
      pa.startDA(daId);
    }
    catch (RemoteException e)
    {
      updateLog("Error starting the Device Adapter " + daId);
    }
  }

  @Override
  public void stopDA(String daId)
  {
    try
    {
      updateLog("Stopping the Device Adapter " + daId);
      pa.stopDA(daId);
    }
    catch (RemoteException e)
    {
      updateLog("Error stopping the Device Adapter " + daId);
    }
  }

  public void parseClick(View v)
  {
    switch (v.getId())
    {
      case R.id.btnStartDA:
        // Start the dialog to start the DA
        try
        {
          List<String> availableDa = new ArrayList<String>(pa.getAvailableDAs().keySet());
          StartDaDialogFragment startDaFrag = StartDaDialogFragment.newInstance(availableDa);
          startDaFrag.show(getFragmentManager(), "startda");
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to start the DAs");
        }
        break;
      case R.id.btnStopDA:
        // Start the dialog to stop the DA
        try
        {
          List<String> availableDa = new ArrayList<String>(pa.getAvailableDAs().keySet());
          StopDaDialogFragment stopDaFrag = StopDaDialogFragment.newInstance(availableDa);
          stopDaFrag.show(getFragmentManager(), "stopda");
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to stop the DAs");
        }
        break;
      case R.id.btnConnectDevices:
        // Start the dialog to connect to a device
        try
        {
          if (!pa.getDADevices().isEmpty())
          {
            if (pa.getDADevices().size() > 1)
            {
              ConnectDevDialogFragment connDevFrag = ConnectDevDialogFragment.newInstance(pa.getDADevices());
              connDevFrag.show(getFragmentManager(), "connectDev");
            }
            else
            {
              Set set = pa.getDADevices().entrySet();
              Map.Entry[] entries = (Map.Entry[])set.toArray(new Map.Entry[set.size()]);
              Map.Entry entry = entries[0];
              this.connectDev((String)entry.getKey(), (String)((ArrayList)entry.getValue()).get(0));
            }
          }
          else
          {
            Toast.makeText(this, "No devices available", Toast.LENGTH_SHORT).show();
          }
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to connect a device");
        }
        break;
      case R.id.btnSendCommand:
        // Start the dialog to send command
        try
        {
          Map<String, List<String>> devicesDa = pa.getDADevices();
          Map<String, List<String>> devicesCommands = new HashMap<>();

          for (DeviceDescription tmpDev : pa.getConnectedDevices())
          {
            String devId = tmpDev.getDeviceID();
            String daId = devicesDa.get(tmpDev.getDeviceID()).get(0);
            List<String> commands = pa.getCommandList(daId);
            devicesCommands.put(devId, commands);
          }

          if (!devicesCommands.isEmpty())
          {
            // Start the dialog to chose the DA to configure
            SendCommandDialogFragment sendCommandFrag = SendCommandDialogFragment.newInstance(devicesCommands);
            sendCommandFrag.show(getFragmentManager(), "sendCommand");
          }
          else
          {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show();
          }
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to send a command");
        }
        break;
      case R.id.btnDisconnectDevices:
        // Start the dialog to disconnect from a device
        try
        {
          List<DeviceDescription> connDev = pa.getConnectedDevices();
          if (!connDev.isEmpty())
          {
            if (connDev.size() > 1)
            {
              List<String> connDevId = new ArrayList<String>();
              for (DeviceDescription tmpDev : connDev)
              {
                connDevId.add(tmpDev.getDeviceID());
              }
              DisconnectDevDialogFragment disconnDevFrag = DisconnectDevDialogFragment.newInstance(connDevId);
              disconnDevFrag.show(getFragmentManager(), "disconnectDev");
            }
            else
            {
              this.disconnectDev(connDev.get(0).getDeviceID());
            }
          }
          else
          {
            Toast.makeText(this, "No devices connected", Toast.LENGTH_SHORT).show();
          }
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to disconnect from a device");
        }
        break;
      case R.id.btnConfigDA:
        // Start the dialog to chose the DA to configure
        try
        {
          Map<String, Capabilities> availableDas = pa.getAvailableDAs();
          Map<String, ComponentName> daConfigActivities = new HashMap<String, ComponentName>();
          for (Capabilities tmpDaCap : availableDas.values())
          {
            if (tmpDaCap.isGuiConfigurable())
            {
              daConfigActivities.put(tmpDaCap.getDaId(), tmpDaCap.getConfigActivityName());
            }
          }
          if (!daConfigActivities.isEmpty())
          {
            ConfigDaDialogFragment configDaFrag = ConfigDaDialogFragment.newInstance(daConfigActivities);
            configDaFrag.show(getFragmentManager(), "configDa");
          }
          else
          {
            Toast.makeText(this, "No configurable DA available", Toast.LENGTH_SHORT).show();
          }
        }
        catch (RemoteException e)
        {
          updateLog("Failed to show the dialog to configure the DA");
        }
        break;
    }
  }

  public void clearLogs(View v)
  {
    //((TextView)findViewById(R.id.logBox)).setText("");
  }

  public void updateLog(String log)
  {
    Log.d(LOGTAG, log);

    final String fLog = log;

    final TextView tv = (TextView)findViewById(R.id.logBox);
    tv.post(new Runnable()
    {
      @Override
      public void run()
      {
        tv.append(fLog + "\n");
      }
    });

    final ScrollView sv = (ScrollView)findViewById(R.id.scrollBox);
    sv.postDelayed(new Runnable()
    {
      @Override
      public void run()
      {
        sv.fullScroll(ScrollView.FOCUS_DOWN);
      }
    }, 100L);
  }

  private class AsyncTaskRunner extends AsyncTask<String, String, Void>
  {
    public AsyncTaskRunner()
    {
    }

    @Override
    protected Void doInBackground(String... strings)
    {
      sendCommand("enableAccelerometerData", "", strings[0]);
      //sendCommand("enableBreathingData", "", strings[0]);
      sendCommand("enableRtoRData", "", strings[0]);
      sendCommand("enableGeneralData", "", strings[0]);
      return null;
    }
  }
}
