package com.shablovskiy91.android.pl2303terminal;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.sin.android.pl2303hxa.R;
import com.sin.android.sinlibs.activities.BaseActivity;
import com.sin.android.sinlibs.base.Callable;
import com.sin.android.sinlibs.utils.InjectUtils;
import com.shablovskiy91.android.usb.pl2303hxa.PL2303Driver;
import com.shablovskiy91.android.usb.pl2303hxa.PL2303Exception;
import java.io.*;
import android.widget.*;
import android.graphics.*;
import android.os.*;

/**
 * PL2303HXA terminal Main Activity
 *
 * Modified by: shablovskiy91
 * Date: 04.04.2021
 */

public class MainActivity extends BaseActivity
{ 
	private Spinner sp_baudrate = null;
	private Spinner sp_SBits = null;
	private Spinner sp_Parity = null;
	private Spinner sp_DBits = null;
	private Button btn_open = null;
	private Button btn_close = null;
	private Button btn_clearsend = null;
	private Button btn_clearlog = null;
	private Button btn_file = null;
	private CheckBox cb_hex = null;
	private CheckBox cb_delay = null;
	private CheckBox cb_ctsrts = null;
	private CheckBox cb_dsrdtr = null;
	private CheckBox cb_xonxoff = null;
	private Button btn_send = null;
	private Button btn_cancel = null;
	private EditText et_send = null;
	private EditText et_delay = null;
	private TextView tv_log = null;
	private ProgressBar pb_sendprogress;

	/*private TextView et_sleep = null;*/
	/*private CheckBox cb_auto = null;*/

	// PL2303 driver
	private PL2303Driver curDriver = null;
	
	public static int PICK_FILE = 1;  // The request code
	private static final String TEXT_CHARSET = "UTF-8"; // Character encoding
	private static final String[] BAUDRATES = { "75", "300", "600", "1200", "2400", "4800", "9600", "19200" };
	private static final String[] SBITS = { "S1", "S2" }; // 0=S1, 2=S2
	private static final String[] PARITY = { "none", "Even" }; // 0=none, 2=even
	private static final String[] DBITS = { "5", "6", "7", "8" }; // data bits
	private static final int FLOW_CONTROL_TIMEOUT = 6000;
	private int timeoutCounter = 0;
	private int progressStatus = 0;
	private byte[] dataBytes;
	private List<Byte> dataList;
	private boolean xOFFStatus;
	private boolean cancelStatus = false;
	private static final int xON = 0x11; //hex=11, dec=17
	private static final int xOFF = 0x13; //hex=13, dec=1
	private boolean readyToSend = true;

	private final Handler handler = new Handler();
	
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() 
	{
		public void onReceive(Context context, Intent intent) 
		{
			String action = intent.getAction();
			if (PL2303Driver.ACTION_PL2303_PERMISSION.equals(action))
			{
				synchronized (this) 
				{
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) 
					{
					AddLog("Autorization succes");
					open();
					} 
					else 
					{
						curDriver = null;
						AddLog("Autorization failed");
					}
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		sharedPreferences = getSharedPreferences("config", MODE_PRIVATE);

		setContentView(R.layout.activity_main);

		initControls();

		// Register the USB broadcast receiver for monitoring authorization
		IntentFilter filter = new IntentFilter(PL2303Driver.ACTION_PL2303_PERMISSION);
		filter.addAction(PL2303Driver.ACTION_PL2303_PERMISSION);
		this.registerReceiver(mUsbReceiver, filter);
	}
	
	private void initControls()
	{
		// find and set all View to this.xx
		InjectUtils.injectViews(this, R.id.class);

		sp_baudrate = (Spinner) findViewById(R.id.sp_baudrate);
		ArrayAdapter<String> adapter1 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, BAUDRATES);
		adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp_baudrate.setAdapter(adapter1);

		sp_SBits = (Spinner) findViewById(R.id.sp_SBits);
		ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, SBITS);
		adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp_SBits.setAdapter(adapter2);

		sp_Parity = (Spinner) findViewById(R.id.sp_Parity);
		ArrayAdapter<String> adapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, PARITY);
		adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp_Parity.setAdapter(adapter3);

		sp_DBits = (Spinner) findViewById(R.id.sp_DBits);
		ArrayAdapter<String> adapter4 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, DBITS);
		adapter4.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp_DBits.setAdapter(adapter4);

		pb_sendprogress = (ProgressBar) findViewById(R.id.pb_sendprogress);

		tv_log.setOnLongClickListener(new View.OnLongClickListener()
		{
			@Override
			public boolean onLongClick(View v)
			{
				//Share text to any app
				shareFile();
				return false;
			}
		});

		// event
		btn_open.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0)
			{
				preOpen();
			}
		});

		btn_close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0)
			{
				close();
			}
		});

		btn_send.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0)
			{
					if (cb_hex.isChecked())
					{
						hexSend();
					}
					else
					{
						charSend();
					}
			}
		});

		btn_cancel.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0)
			{
				cancelStatus = true;
				refreshButton();
			}
		});

		btn_clearsend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0)
			{
				clearSend();
			}
		});

		btn_clearlog.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0)
			{
				clearLog();
			}
		});

		btn_file.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View arg0)
			{
				fileOpen();
			}
		});

	}

	private int logct = 0;
	
	private void AddLog(String ftm, Object... args) 
	{
		String log = String.format(ftm, args);
		safeCall(new Callable()
		{
			@Override
			public void call(Object... args)
			{
				++logct;
				tv_log.append(String.format("%03d ", logct));
				tv_log.append(args[0].toString());
				tv_log.append("\n");

				((ScrollView) tv_log.getParent()).post(new Runnable() 
				{
					@Override
					public void run() 
					{
						((ScrollView) tv_log.getParent()).fullScroll(ScrollView.FOCUS_DOWN);
					}
				});
			}
		}, log);
	}
	
	private void AddText(String recsString) 
	{
		safeCall(new Callable()
		{
			@Override
			public void call(Object... args)
			{
				tv_log.append(args[0].toString());
				((ScrollView) tv_log.getParent()).post(new Runnable() 
				{
					@Override
					public void run() 
					{
							((ScrollView) tv_log.getParent()).fullScroll(ScrollView.FOCUS_DOWN);
					}
				});
			}
		}, recsString);
	}

	// Recruit PL2303 and request to open
	private void preOpen() 
	{
		// Try the first PL2303 device
		List<UsbDevice> devices = PL2303Driver.getAllSupportedDevices(this);
		if (devices.size() == 0) 
		{
			AddLog(" Please insert PL2303HXA device");
		} 
		else 
		{
			AddLog(" Current PL2303HXA device:");
			for (UsbDevice d : devices) 
			{
				AddLog(" " + d.getDeviceId());
			}
			// Use the first PL2303HXA device found
			PL2303Driver dev = new PL2303Driver(this, devices.get(0));
			openPL2302Device(dev);
		}

		// Open the selection dialog to select the device
		/*
		 * PL2303Selector.createSelectDialog(this, 0, false, new
		 * PL2303Selector.Callback() {
		 *
		 * @Override public boolean whenPL2303Selected(PL2303Driver driver) {
		 * openPL2302Device(driver); return true;} }).show();
		 */
	}

	private void openPL2302Device(PL2303Driver dev)
	{
		if (dev != null) 
		{
			curDriver = dev;
			if (curDriver.checkPermission())
			{
				// Open directly if authorized
				open();
			}
		}
	}
	
	private String getReceive(ArrayList<Byte> recs)
	{
		StringBuffer sb = new StringBuffer();
		/*sb.append("roger that: ");*/
		if (cb_hex.isChecked())
		{
			for (Byte b : recs)
			{
				sb.append(String.format("%02x ", b));
			}
		} 
		else
		{
			int len = recs.size();
			byte[] data = new byte[len];
			for (int i = 0; i < len; ++i) 
			{
				data[i] = recs.get(i);
			}
			try
			{
				sb.append(new String(data, TEXT_CHARSET));
			} 
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
				AddLog("Transcoding failed");
			}
		}
		return sb.toString();
	}

	// Receive thread, always receive
	private void startReceiveThread() 
	{
		//Set on DTR
		try 
		{
			curDriver.setDTR(true);
		} 
		catch (PL2303Exception e) 
		{
			AddLog(" Error on setDTR: ");
		}
		
		// Set on RTS
		try 
		{
			curDriver.setRTS(true);
		} 
		catch (PL2303Exception e) 
		{
			AddLog(" Error on setRTS: ");
		}
		
		asynCall(new Callable() 
		{
			@Override
			public void call(Object... arg0) 
			{
				AddLog(" Start receiving ");
				
				ArrayList<Byte> recs = new ArrayList<Byte>();
				long pretime = System.currentTimeMillis();
				while (curDriver != null) 
				{
					PL2303Driver tDriver = curDriver;
					byte dat = 0;
					synchronized (tDriver) 
					{
						dat = tDriver.read();
					}
					if (tDriver.isReadSuccess()) 
					{
						//read XON/XOFF Status
						if (cb_xonxoff.isChecked())
						{
							if (dat==xOFF)
							{
								xOFFStatus = true;
								handler.post(new Runnable()
								{
									public void run()
									{
										cb_xonxoff.setTextColor(Color.RED);
									}
								});
							}
							if (dat==xON)
							{
								xOFFStatus = false;
								handler.post(new Runnable()
								{
									public void run()
									{
										cb_xonxoff.setTextColor(Color.GREEN);
									}
								});
							}
						}
						recs.add(dat);
					} 
					else
					{
						// No data Received
						pretime = 0;
						try 
						{
							Thread.sleep(5);
						} 
						catch (InterruptedException e) 
						{
							e.printStackTrace();
						}

					}

					
					if (cb_hex.isChecked())
					{
						//At least 10000ms to display the received dat
						if ((System.currentTimeMillis() - pretime) > 10000 && recs.size() > 0) 
						{
							AddLog(getReceive(recs));
							recs.clear();
							pretime = System.currentTimeMillis();
						}
						if (recs.size() == 0)
							pretime = System.currentTimeMillis();
					}
					else
					{
						//At least 200ms to display the received dat
						if ((System.currentTimeMillis() - pretime) > 200 && recs.size() > 0) 
						{
							AddText(getReceive(recs));
							recs.clear();
							pretime = System.currentTimeMillis();
						}
						if (recs.size() == 0)
							pretime = System.currentTimeMillis();
					}
				}
				AddLog("End of reception");
			}
		});
	}
	
	// Open the serial port
	private void open()
	{
		if (curDriver == null)
		{
			return;
		}
		try
		{
			if (curDriver.isOpened()) 
			{
				curDriver.cleanRead();
				curDriver.close();
			}
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}

		AddLog(" Open the serial port ", curDriver.getDeviceID());
		try 
		{
			//set baudrate from spinner selected item
			curDriver.setBaudRate(Integer.parseInt(sp_baudrate.getSelectedItem().toString()));
			//set Stop Bits
			String SBitsItem = sp_SBits.getSelectedItem().toString();
			if (SBitsItem == "S2") 
			{
				curDriver.setSBits(2);
			}
			else
			{
				curDriver.setSBits(0);
			}
			//set Parity
			String ParityItem = sp_Parity.getSelectedItem().toString();
			if (ParityItem == "Even") 
			{
				curDriver.setParity(2);
			}
			else {
				curDriver.setParity(0);
			}
			//set Data Bits
			curDriver.setDBits(Integer.parseInt(sp_DBits.getSelectedItem().toString()));
			
			curDriver.open();

			startReceiveThread();
			
			refreshButton();
			
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // Keep screen On, when opened
			
		} 
		catch (PL2303Exception e)
		{
			AddLog(" Open failed ");
			AddLog(e.getMessage());
			e.printStackTrace();
		}
	}

	private void close() 
	{
		//Set off RTS
		try 
		{
			curDriver.setRTS(false);
		}

		catch (PL2303Exception e) 
		{
			AddLog(" Error setRTS off: ");
		}

		// Set off DTR
		try 
		{
			curDriver.setDTR(false);
		} 
		catch (PL2303Exception e) 
		{
			AddLog(" Error setDTR off: ");
		}
		
		AddLog(" Close the serial port ");
		synchronized (curDriver) 
		{
			curDriver.cleanRead();
			curDriver.close();
		}
		curDriver = null;
		AddLog(" Close success ");

		refreshButton();
		
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
	}

	/**
	 * HEX format: "xx xx xx xx .."
	 * Send Function
	 */
	
	private void hexSend()
	{
		
		if (curDriver == null) 
		{
			AddLog(" Please open the serial port first ");
			return;
		}
		
		//Get data to string
		String ins = et_send.getText().toString();
		ins = ins.trim();
	
		if (ins.length() > 0) 
		{
			String[] hexs = ins.split(" ");
			dataList = new ArrayList<Byte>();
			boolean okflag = true;
			for (String hex : hexs) 
			{
				try 
				{
					int d = Integer.parseInt(hex, 16);
					if (d > 255) 
					{
						AddLog("%s greater than 0xff ", hex);
						okflag = false;
					} 
					else 
					{
						dataList.add((byte) d);
					}
				} 
				catch (NumberFormatException e) 
				{
					AddLog("%s Not a hexadecimal number ", hex);
					e.printStackTrace();
					okflag = false;
				}
			}

			int dataSize = dataList.size();
			pb_sendprogress.setMax(dataSize);
			progressStatus = 0;
			
			// Send Readed HEX data
			if (okflag && dataList.size() > 0)
			{
				new Thread (new Runnable()
					{
						public void run()
						{
							handler.post(new Runnable()
							{
									public void run()
									{
										// 0 - vis; 4 - unvis; 8 - gone
										pb_sendprogress.setVisibility(View.VISIBLE);
										btn_send.setVisibility(View.GONE);
										btn_cancel.setVisibility(View.VISIBLE);
										cb_ctsrts.setTextColor(Color.GRAY);
										cb_dsrdtr.setTextColor(Color.GRAY);
									}
								
							});
						
							readyToSend = true;
							
							for (Byte b : dataList)  // for cycle
							{
								//if Cancel button was pressed
								if (cancelStatus == true)
								{
									AddLog("Operation canceled");
									handler.post(new Runnable()
										{
											public void run()
											{
												// 0 - vis; 4 - unvis; 8 - gone
												pb_sendprogress.setVisibility(View.GONE);
												btn_cancel.setVisibility(View.INVISIBLE);
												btn_send.setVisibility(View.VISIBLE);
											}
										});
									cancelStatus = false;
									return;
								}

								charDelay();

								checkDSR();
								checkCTS();
								checkXonXoff();
								
								if (readyToSend == false)
								{
									return;
								}

								progressStatus++;

								curDriver.write(b);
								handler.post(new Runnable()
								{
									public void run()
									{
										pb_sendprogress.setProgress(progressStatus);
									}
								});
							}

							handler.post(new Runnable()
							{
								public void run()
								{
									// 0 - vis; 4 - unvis; 8 - gone
									pb_sendprogress.setVisibility(View.GONE);
									btn_cancel.setVisibility(View.GONE);
									btn_send.setVisibility(View.VISIBLE);
								}
							});
						}
					}).start();
			}
			
			else 
			{
				AddLog(" The hexadecimal data to send is empty ");
			}
		}
		
	}
	
	
	/**
	 * Character Send Function
	 * Encoding by TEXT_CHARSET. By default: "UTF-8"
	 */
	private void charSend()
	{
		if (curDriver == null) 
		{
			AddLog(" Please open the serial port first ");
			return;
		}
		
		//Get data to string
		int stringLenght = et_send.length();
		pb_sendprogress.setMax(stringLenght);
		progressStatus = 0;
		
		String ins = et_send.getText().toString();
		
		try
		{
			dataBytes = ins.getBytes(TEXT_CHARSET);
		}
		catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
			AddLog(" Transformation code failed ");
		}
		
		new Thread (new Runnable()
		{
			public void run()
			{
				handler.post(new Runnable()
				{
					public void run()
					{
						// 0 - vis; 4 - unvis; 8 - gone
						pb_sendprogress.setVisibility(View.VISIBLE);
						btn_send.setVisibility(View.GONE);
						btn_cancel.setVisibility(View.VISIBLE);
						cb_ctsrts.setTextColor(Color.GRAY);
						cb_dsrdtr.setTextColor(Color.GRAY);
					}
				});
				
				for (Byte b : dataBytes)// for cycle
				{
					//if Cancel button was pressed
					if (cancelStatus)
					{
						AddLog("Operation canceled");
						handler.post(new Runnable()
						{
							public void run()
							{
								// 0 - vis; 4 - unvis; 8 - gone
								pb_sendprogress.setVisibility(View.GONE);
								btn_cancel.setVisibility(View.INVISIBLE);
								btn_send.setVisibility(View.VISIBLE);
							}
						});
						cancelStatus = false;
						return;
					}

					charDelay();

					checkDSR();
					checkCTS();
					checkXonXoff();

					if (readyToSend == false)
					{
						return;
					}

					progressStatus++;

					curDriver.write(b);

					handler.post(new Runnable()
					{
						public void run()
						{
							pb_sendprogress.setProgress(progressStatus);
						}
					});
				}

				handler.post(new Runnable()
				{
					public void run()
					{
						// 0 - vis; 4 - unvis; 8 - gone
						pb_sendprogress.setVisibility(View.GONE);
						btn_cancel.setVisibility(View.GONE);
						btn_send.setVisibility(View.VISIBLE);
					}
				});
			}
		}).start();
	}
	
	private void charDelay()
	{
		// Byte Delay
		int sleep = 0;
		if (cb_delay.isChecked()) 
		{
			try 
			{
				int v = Integer.parseInt(et_delay.getText().toString());
				// If delay is more 0 mS
				if (v > 0)
				{
					sleep = v;
				}
			} 
			catch (Exception e) 
			{
				AddLog("Char Delay Value error");
			}
			
			try 
			{
				Thread.sleep(sleep);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
	}

	private void checkCTS()
	{
		//if CTS/RTS Flow control is checked...
		if (cb_ctsrts.isChecked())
		{

			handler.post(new Runnable()
				{
					public void run()
					{
						cb_ctsrts.setTextColor(Color.GREEN);
					}
				});
			timeoutCounter = 0;
			// Check CTS status. While its false, wait 1 mS. If > timeout, AddLog, return.
			while (curDriver.getCTS()==false)
			{
				//Wait 1 mS
				try 
				{
					Thread.sleep(1);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				//Increment timeout
				timeoutCounter++;

				if (timeoutCounter > FLOW_CONTROL_TIMEOUT)
				{
					AddLog("CTS down");
					handler.post(new Runnable()
						{
							public void run()
							{
								cb_ctsrts.setTextColor(Color.RED);
								// 0 - vis; 4 - unvis; 8 - gone
								pb_sendprogress.setVisibility(View.INVISIBLE);
								btn_cancel.setVisibility(View.GONE);
								btn_send.setVisibility(View.VISIBLE);
							}
						});
					timeoutCounter = 0;
					readyToSend = false;
					return;
				}
			}
		}
	}
	
	private void checkDSR()
	{
		//if DSR/DTR Flow control is checked...
		if (cb_dsrdtr.isChecked())
		{
			handler.post(new Runnable()
				{
					public void run()
					{
						cb_dsrdtr.setTextColor(Color.GREEN);
					}
				});
			timeoutCounter = 0;
			// Check DSR status. While its false, wait 1 mS. When > timeout, AddLog, return.
			while (curDriver.getDSR() == false)
			{
				//Wait 1 mS
				try 
				{
					Thread.sleep(1);
				}
				catch (InterruptedException e) 
				{
					e.printStackTrace();
				}
				//Increment timeout
				timeoutCounter++;

				if (timeoutCounter > FLOW_CONTROL_TIMEOUT)
				{
					AddLog("DSR down");
					handler.post(new Runnable()
						{
							public void run()
							{
								// 0 - vis; 4 - unvis; 8 - gone
								pb_sendprogress.setVisibility(View.INVISIBLE);
								btn_cancel.setVisibility(View.GONE);
								btn_send.setVisibility(View.VISIBLE);
							}
						});
					timeoutCounter = 0;
					readyToSend = false;
					return;
				}
			}
		}
	}

	private void checkXonXoff()
	{
		//if Software Flow control is checked
		if (cb_xonxoff.isChecked())
		{
			timeoutCounter = 0;
			// Check xoff status. If true, wait timeout. Check again, if true AddLog, return
			while (xOFFStatus == true)
			{
				//Wait 1 mS
				try 
				{
					Thread.sleep(1);
				}
				catch (InterruptedException e) 
				{
					e.printStackTrace();
				}
				//Increment timeout
				timeoutCounter++;

				if (timeoutCounter > FLOW_CONTROL_TIMEOUT)
				{
					AddLog("Xon/Xoff timeout");
					handler.post(new Runnable()
						{
							public void run()
							{
								// 0 - vis; 4 - unvis; 8 - gone
								pb_sendprogress.setVisibility(View.INVISIBLE);
								btn_cancel.setVisibility(View.GONE);
								btn_send.setVisibility(View.VISIBLE);
							}
						});
					xOFFStatus = false;
					timeoutCounter = 0;
					readyToSend = false;
					return;
				}
			}
		}
	}
	
	private void clearSend()
	{
		et_send.setText("");
		Toast.makeText(getApplicationContext()," Text to send is cleared ",Toast.LENGTH_SHORT).show();
		refreshButton();
	}
	
	private void clearLog()
	{
		tv_log.setText("");
		Toast.makeText(getApplicationContext()," Log is cleared ",Toast.LENGTH_SHORT).show();
		refreshButton();
	}
	
	private void fileOpen()
	{
		Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
		fileIntent.setType("text/*"); // intent type to filter application based on your requirement
		startActivityForResult(fileIntent, PICK_FILE);
	}
		
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE) 
		{
            if (resultCode == RESULT_OK) 
			{
                // User pick the file
                Uri uri = data.getData();
                String fileContent = readTextFile(uri);
				et_send.setText(fileContent);
				/*Toast.makeText(this, fileContent, Toast.LENGTH_LONG).show();*/
			} 
			else 
			{
                return;
            }
        }
    }
		
	private String readTextFile(Uri uri)
	{
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try
		{
            reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
            int c = 0;
            while ((c = reader.read()) != -1)
			{
                builder.append((char) c);
            }
        } 
		catch (IOException e) 
		{
            e.printStackTrace();
        } 
		finally 
		{
            if (reader != null)
			{
                try
				{
                    reader.close();
                } 
				catch (IOException e) 
				{
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

	
	private void shareFile()
	{
		String filePath = "/mnt/sdcard/";
		
		File f = new File(filePath);
		String shareText = tv_log.getText().toString();
	
		Intent intentShareFile = new Intent(Intent.ACTION_SEND);
		
		intentShareFile.setType("text/*");
		intentShareFile.putExtra(Intent.EXTRA_TEXT, shareText);

		this.startActivity(Intent.createChooser(intentShareFile, f.getName()));
		
	}
	
	private void refreshButton() 
	{
		btn_open.setVisibility(curDriver != null ? View.GONE : View.VISIBLE);
		btn_close.setVisibility(curDriver == null ? View.GONE : View.VISIBLE);
		sp_baudrate.setEnabled(curDriver == null);
		sp_SBits.setEnabled(curDriver == null);
		sp_Parity.setEnabled(curDriver == null);
		sp_DBits.setEnabled(curDriver == null);
		btn_send.setEnabled(curDriver != null);
		et_delay.setEnabled(curDriver == null);
		//btn_cancel.setEnabled(btn_send.getVisibility() == 4);
		//cb_delay.setEnabled(curDriver == null);
		//cb_ctsrts.setEnabled(curDriver == null);
		//cb_xonxoff.setEnabled(curDriver == null);
		
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		this.unregisterReceiver(mUsbReceiver);
	}

	private SharedPreferences sharedPreferences = null;

	@Override
	protected void onPause() 
	{
		Editor edit = sharedPreferences.edit();
		edit.putBoolean("cb_hex", cb_hex.isChecked());
		edit.putBoolean("cb_ctsrts", cb_ctsrts.isChecked());
		edit.putBoolean("cb_dsrdtr", cb_dsrdtr.isChecked());
		edit.putBoolean("cb_xonxoff", cb_xonxoff.isChecked());
		edit.putBoolean("cb_delay", cb_delay.isChecked());
		
		edit.putInt("sp_baudrate", sp_baudrate.getSelectedItemPosition());
		edit.putInt("sp_SBits", sp_SBits.getSelectedItemPosition());
		edit.putInt("sp_Parity", sp_Parity.getSelectedItemPosition());
		edit.putInt("sp_DBits", sp_DBits.getSelectedItemPosition());
		
		edit.putString("et_send", et_send.getText().toString());
		edit.putString("et_delay", et_delay.getText().toString());
		
		edit.commit();
		super.onPause();
	}

	@Override
	protected void onResume() 
	{
		super.onResume();
			cb_hex.setChecked(sharedPreferences.getBoolean("cb_hex", true));
			cb_delay.setChecked(sharedPreferences.getBoolean("cb_delay", true));
			cb_ctsrts.setChecked(sharedPreferences.getBoolean("cb_ctsrts", true));
			cb_dsrdtr.setChecked(sharedPreferences.getBoolean("cb_dsrdtr", true));
			cb_xonxoff.setChecked(sharedPreferences.getBoolean("cb_xonxoff", true));
			sp_baudrate.setSelection(sharedPreferences.getInt("sp_baudrate", 0));
			sp_SBits.setSelection(sharedPreferences.getInt("sp_SBits", 0));
			sp_Parity.setSelection(sharedPreferences.getInt("sp_Parity", 0));
			sp_DBits.setSelection(sharedPreferences.getInt("sp_DBits", 0));
			et_delay.setText(sharedPreferences.getString("et_delay", "0"));
			//et_send.setText(sharedPreferences.getString("et_send", "0"));
		
			refreshButton();
	}
}
