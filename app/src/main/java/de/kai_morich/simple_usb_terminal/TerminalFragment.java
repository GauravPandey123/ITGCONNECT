package de.kai_morich.simple_usb_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.*;
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private final BroadcastReceiver broadcastReceiver;
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private ControlLines controlLines;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = true;
    private boolean controlLinesEnabled = false;
    private boolean pendingNewline = false;
    private int cmdid,meter_type=2,preMeter_type=2; //0-res,1-pro,2-non-dlms
    private String newline = TextUtil.newline_crlf;
    Timer timer;
    TimerTask timerTask;
    final Handler handler = new Handler();
    SerialSocket socket;
    UsbDeviceConnection usbConnection;
    UsbSerialDriver driver;
    UsbManager usbManager;
    private String SNRM="7E A0 07 03 41 93 5A 64 7E";
    private String SNRMDissconnect="7E A0 07 03 41 53 56 A2 7E";
    private String SNRMInit="7E A0 1E 03 41 93 98 5E 81 80 12 05 01 80 06 01 80 07 04 00 00 00 01 08 04 00 00 00 01 53 3B 7E";
    private String COSEMOPEN_Type_RES="7E A0 44 03 41 10 B3 E1 E6 E6 00 60 36 A1 09 06 07 60 85 74 05 08 01 01 8A 02 07 80 8B 07 60 85 74 05 08 02 01 AC 0A 80 08 41 42 43 44 45 46 47 48 BE 10 04 0E 01 00 00 00 06 5F 1F 04 00 00 18 1D FF FF 83 49 7E";
    private String COSEMResponse_RES="7EA0374103302179E6E7006129A109060760857405080101A203020101A305A10302010DBE10040E0800065F1F040000101402000007AF3C7E";
    private String COSEMResponse_PRO="7EA0374103302179E6E7006129A109060760857405080101A203020100A305A103020100BE10040E0800065F1F040000101402000007BC857E";
    private String COSEMOPEN_Type_PRO="7E A0 4C 03 41 10 6B 04 E6 E6 00 60 3E A1 09 06 07 60 85 74 05 08 01 01 8A 02 07 80 8B 07 60 85 74 05 08 02 01 AC 12 80 10 30 31 32 33 34 35 36 37 38 39 41 42 43 44 45 46 BE 10 04 0E 01 00 00 00 06 5F 1F 04 00 00 18 1D FF FF C4 92 7E";
    private String SerialNumber="7E A0 19 03 41 32 3A BD E6 E6 00 C0 01 81 00 01 00 00 60 01 00 FF 02 00 8C 6D 7E";
    private String dlmskWh="7E A0 19 03 41 54 0A BB E6 E6 00 C0 01 81 00 03 01 00 01 08 00 FF 02 00 37 A5 7E";
    private String dlmskVAh="7E A0 19 03 41 76 1A B9 E6 E6 00 C0 01 81 00 03 01 00 09 08 00 FF 02 00 6F 84 7E";
    private String dlmskWMD="7E A0 19 03 41 98 6A B7 E6 E6 00 C0 01 81 00 04 01 00 01 06 00 FF 02 00 6D 2D 7E";
    private String dlmskVAMD="7E A0 19 03 41 BA 7A B5 E6 E6 00 C0 01 81 00 04 01 00 09 06 00 FF 02 00 35 0C 7E";
    private String nonDLMSVer="45 45 4F 56 45 52 0D 0A";
    private String nonDLMSSerialNumber="45 45 4F 48 48 54 0D 0A";
    private String parameterDisplay="";


    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(Constants.INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        timer.schedule(timerTask, 2000, 2000);
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {

                        //code to run after every 5 seconds
                        switch (cmdid) {
                            case 1:
                                if(meter_type==2)
                                {
                                    if(receiveText.getText().toString().length()<5)
                                    {
                                        meter_type=0;
                                        cmdid=0;
                                        try {

                                            //usbSerialPort.close();
                                            //usbSerialPort.open(usbConnection);
                                            baudRate = 9600;

                                            disconnect();
                                            Thread.sleep(1000);
                                            Reconnect(true);
                                            //usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                                            //SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
                                            //socket.disconnect();
                                            //socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);

                                            //service.connect(socket);
                                            //usbSerialPort.open(usbConnection);

                                            Thread.sleep(1000);
                                            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
                                            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
                                            //onSerialConnect();

                                        } catch (Exception e) {
                                            onSerialConnectError(e);
                                        }
                                    }

                                }

                                break;
                            case 2:
                                if(meter_type==2)
                                {

                                    String meterRx=receiveText.getText().toString();
                                    int start_post=8+2+1+1+3+2;
                                    start_post=start_post*2;
                                    String serialno=meterRx.substring(start_post,start_post+8);

                                    serialno= String.format("%07d",getIntVal(serialno,4));

                                    parameterDisplay="Serial#   :"+ serialno+"\n";

                                    start_post+=(4*2);
                                    start_post+=(11*2);
                                    String kWh=meterRx.substring(start_post,start_post+8);
                                    NumberFormat fr= new DecimalFormat("#0.0000");
                                    kWh=fr.format(getIntVal(kWh,4)/10000.0);
                                    //kWh=String.format("%f",getIntVal(kWh,4)/10000.0);
                                    start_post+=(4*2);
                                    String kVAh=meterRx.substring(start_post,start_post+8);
                                    NumberFormat fr2= new DecimalFormat("#0.0000");
                                    kVAh=fr2.format(getIntVal(kVAh,4)/10000.0);

                                    //kVAh=String.format("%f",getIntVal(kVAh,4)/10000.0);

                                    start_post+=(4*2);
                                    start_post+=(4*2);
                                    String kWMD=meterRx.substring(start_post,start_post+4);
                                    NumberFormat fr3= new DecimalFormat("#0.000");
                                    kWMD=fr3.format(getIntVal(kWMD,2)/1000.0);
                                    //kWMD=String.format("%f",getIntVal(kWMD,4)/1000.0);

                                    parameterDisplay+="kWh  :"+ kWh +"\n";
                                    parameterDisplay+="kVAh  :"+ kVAh +"\n";
                                    parameterDisplay+="kW MD  :"+ kWMD +"\n";
                                    cmdid = 9;
                                }
                                break;
                            case 4:
                                preMeter_type = meter_type;
                                if (receiveText.length() > 10) {
                                    if(meter_type==0)
                                        if (receiveText.getText().toString().contains(COSEMResponse_PRO) == false)
                                            meter_type = 1;
                                }

                                if (preMeter_type != meter_type) {
                                    if(meter_type==2)
                                        cmdid = 1;
                                    else
                                        cmdid=0;
                                }

                                break;
                            case 5:

                                parameterDisplay="Serial#   :"+ hexToASCII(receiveText.getText().toString().substring(34,48))+"\n";
                                break;
                            case 6:
                                String kWh=receiveText.getText().toString().substring(32,40);
                                NumberFormat fr= new DecimalFormat("#0.000");
                                if(meter_type==0)
                                    kWh=fr.format(getdlmsIntVal(kWh,4)/100.0);
                                else
                                    kWh=fr.format(getdlmsIntVal(kWh,4)/1000.0);

                                parameterDisplay+="kWh  :"+ kWh +"\n";

                                break;
                            case 7:
                                String kVAh=receiveText.getText().toString().substring(32,40);
                                NumberFormat fr2= new DecimalFormat("#0.000");
                                if(meter_type==0)
                                    kVAh=fr2.format(getdlmsIntVal(kVAh,4)/100.0);
                                else
                                    kVAh=fr2.format(getdlmsIntVal(kVAh,4)/1000.0);

                                parameterDisplay+="kVAh  :"+ kVAh +"\n";

                                break;
                            case 8:
                                String kWMD=receiveText.getText().toString().substring(32,40);
                                NumberFormat fr3= new DecimalFormat("#0.000");
                                if(meter_type==0)
                                    kWMD=fr3.format(getdlmsIntVal(kWMD,4)/100.0);
                                else
                                    kWMD=fr3.format(getdlmsIntVal(kWMD,4)/1000.0);


                                parameterDisplay+="kW MD  :"+ kWMD +"\n";

                                break;
                            case 9:
                                String kVAMD=receiveText.getText().toString().substring(32,40);
                                NumberFormat fr4= new DecimalFormat("#0.000");
                                if(meter_type==0)
                                    kVAMD=fr4.format(getdlmsIntVal(kVAMD,4)/100.0);
                                else
                                    kVAMD=fr4.format(getdlmsIntVal(kVAMD,4)/1000.0);


                                parameterDisplay+="kVA MD  :"+ kVAMD +"\n";

                                break;
                        }
                        switch(cmdid) {
                            case 0:
                                receiveText.setText("");
                                if(meter_type==2)
                                    send(nonDLMSVer);
                                else
                                    send(SNRMDissconnect);
                                break;
                            case 1:
                                receiveText.setText("");
                                if(meter_type==2)
                                    send(nonDLMSSerialNumber);
                                else
                                    send(SNRM);
                                break;
                            case 2:
                                receiveText.setText("");
                                send(SNRMInit);
                                break;
                            case 3:
                                receiveText.setText("");
                                if(meter_type==0)
                                    send(COSEMOPEN_Type_RES);
                                else if(meter_type==1)
                                    send(COSEMOPEN_Type_PRO);
                                break;
                            case 4:
                                receiveText.setText("");
                                send(SerialNumber);
                                break;
                            case 5:

                                receiveText.setText("");
                                send(dlmskWh);

                                break;
                            case 6:
                                receiveText.setText("");
                                send(dlmskVAh);

                                break;
                            case 7:
                                receiveText.setText("");
                                send(dlmskWMD);

                                break;
                            case 8:
                                receiveText.setText("");
                                send(dlmskVAMD);

                                break;
                            case 9:
                                receiveText.setText(parameterDisplay);
                                timer.cancel();
                                break;
                        }

                        cmdid++;
                    }
                });
            }
        };
    }

    public long getIntVal(String str, int no_of_bytes)
    {
        int onebyte=0;
        long sum=0;
        no_of_bytes=no_of_bytes*2;
        for(int iTemp=no_of_bytes;iTemp>0;iTemp-=2)
        {
            sum<<=8;
            onebyte=Integer.parseInt(str.substring(iTemp-2,iTemp),16);

            sum += onebyte;

        }
        return sum;
    }
    public long getdlmsIntVal(String str, int no_of_bytes)
    {
        int onebyte=0;
        long sum=0;
        no_of_bytes=no_of_bytes*2;
        for(int iTemp=0;iTemp<no_of_bytes;iTemp+=2)
        {
            sum<<=8;
            onebyte=Integer.parseInt(str.substring(iTemp,iTemp+2),16);

            sum += onebyte;

        }
        return sum;
    }
    public static String hexToASCII(String hex)
    {
        // initialize the ASCII code string as empty.
        String ascii = "";

        for (int i = 0; i < hex.length(); i += 2) {

            // extract two characters from hex string
            String part = hex.substring(i, i + 2);

            // change it into base 16 and typecast as the character
            char ch = (char)Integer.parseInt(part, 16);

            // add this char to final ASCII string
            ascii = ascii + ch;
        }

        return ascii;
    }
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
        if(controlLinesEnabled && controlLines != null && connected == Connected.True)
            controlLines.start();
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        if(controlLines != null)
            controlLines.stop();
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);




        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        controlLines = new ControlLines(view);
        cmdid=0;
        startTimer();
        //cmdid=0;
        //t.schedule(tt,1000,5000);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        menu.findItem(R.id.controlLines).setChecked(controlLinesEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.controlLines) {
            controlLinesEnabled = !controlLinesEnabled;
            item.setChecked(controlLinesEnabled);
            if (controlLinesEnabled) {
                controlLines.start();
            } else {
                controlLines.stop();
            }
            return true;
        } else if (id == R.id.sendBreak) {
            try {
                usbSerialPort.setBreak(true);
                Thread.sleep(100);
                status("send BREAK");
                usbSerialPort.setBreak(false);
            } catch (Exception e) {
                status("send BREAK failed: " + e.getMessage());
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        //UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);


            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            //SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }
    private void Reconnect(Boolean permissionGranted) {
        usbSerialPort = driver.getPorts().get(portNum);
        //UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);


            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            //SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }
    private void disconnect() {
        connected = Connected.False;
        controlLines.stop();
        service.disconnect();
        usbSerialPort = null;
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if (hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if(hexEnabled) {
            receiveText.append(TextUtil.toHexString(data));
        } else {
            String msg = new String(data);
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        if(controlLinesEnabled)
            controlLines.start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    class ControlLines {
        private static final int refreshInterval = 200; // msec

        private final Handler mainLooper;
        private final Runnable runnable;
        private final LinearLayout frame;
        private final ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            mainLooper = new Handler(Looper.getMainLooper());
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            frame = view.findViewById(R.id.controlLines);
            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            rtsBtn.setOnClickListener(this::toggle);
            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (connected != Connected.True) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) { ctrl = "RTS"; usbSerialPort.setRTS(btn.isChecked()); }
                if (btn.equals(dtrBtn)) { ctrl = "DTR"; usbSerialPort.setDTR(btn.isChecked()); }
            } catch (IOException e) {
                status("set" + ctrl + " failed: " + e.getMessage());
            }
        }

        private void run() {
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            frame.setVisibility(View.VISIBLE);
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS)) rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS)) ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR)) dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR)) dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))   cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))   riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            frame.setVisibility(View.GONE);
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(false);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            riBtn.setChecked(false);
        }
    }

}
