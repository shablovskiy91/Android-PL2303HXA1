package com.shablovskiy91.android.usb.pl2303hxa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * PL2303HXA driver <br/>
 * 
 * @author trb
 * @date 2013-8-16
 * 
 * @version 1.1
 * @verinfo V1.1<br/>
 *          2014-04-17<br/>
 *          Designed for input and output streams
 *          <hr/>
 *
 * Modified by: shablovskiy91
 * Date: 04.04.2021
 */

@SuppressLint("DefaultLocale")
public class PL2303Driver 
{
	/**
	 * Version flag
	 */
	public static String VERSION = "1.1";

	/**
	 * Debug flag
	 */
	public static String TAG = "PL2303HXADriver";
	/**
	 * Permission sign
	 */
	public static final String ACTION_PL2303_PERMISSION = "com.sin.android.USB_PERMISSION";

	/**
	 * Unmount the USB device
	 */
	public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

	/**
	 * PL303HXA Product ID
	 */
	public static final int PL2303HXA_PRODUCT_ID = 0x2303;

	// time out
	private final int TimeOut = 100;
	private final int transferTimeOut = TimeOut;
	private int readTimeOut = TimeOut;
	private int writeTimeOut = TimeOut;

	private final Context context;

	private final UsbManager usbManager;
	private final UsbDevice usbDevice;
	private UsbDeviceConnection usbDeviceConnection;
	private UsbInterface usbInterface;
	private UsbEndpoint uein;
	private UsbEndpoint ueout;
	private UsbEndpoint uectrl;
	
	private boolean opened = false;
	private boolean mStopReadStatusThread = false;
	
	/*private int mStatus = 0;*/
	private volatile Thread mReadStatusThread = null;
	private final Object mReadStatusThreadLock = new Object();
	private PL2303Exception mReadStatusException = null;
	
	// Serial port setup constants
	private int baudRate = 115200; // BAUDRATE
	private int SBits = 0; // Stop Bits
	private int Parity = 0; // Parity
	private int DBits = 8; // Data Bits
	
	// Status of DTR/RTS Lines
	private int mControlLines = 0;

	// Status of DSR/CTS/DCD/RI Lines
	private byte mStatusLines = 0;
	
	// Transceiver properties
	private static final int MAX_SENDLEN = 1;
	private static final int SECVBUF_LEN = 40960;
	private static final int SENDBUF_LEN = MAX_SENDLEN;
	
	// USB control commands
	private static final int SET_LINE_REQUEST_TYPE = 0x21;
	private static final int SET_LINE_REQUEST = 0x20;
	private static final int BREAK_REQUEST_TYPE = 0x21;
	private static final int BREAK_REQUEST = 0x23;
	private static final int BREAK_OFF = 0x0000;
	private static final int GET_LINE_REQUEST_TYPE = 0xa1;
	private static final int GET_LINE_REQUEST = 0x21;
	private static final int SET_CONTROL_REQUEST_TYPE = 0x21;
	private static final int SET_CONTROL_REQUEST = 0x22;

	// RS232 Line constants
	private static final int FLOWCONTROL_TIMEOUT = 500;
	private static final int CONTROL_DTR = 0x01;
	private static final int CONTROL_RTS = 0x02;
	private static final int UART_DCD = 0x01;
	private static final int UART_DSR = 0x02;
	private static final int UART_RING = 0x08;
	private static final int UART_CTS = 0x80;
	

	// Buffer zone
	private final byte[] recv_buf = new byte[SECVBUF_LEN];
	private final byte[] send_buf = new byte[SENDBUF_LEN];
	
	private long receivecount = 0, sendcount = 0;

	/**
	 * Determine whether it is a supported USB device type, that is, whether it is a PL2303HXA device
	 * 
	 * @param device
	 *            USB device
	 * @return true means support, false means not support
	 */
	public static boolean isSupportedDevice(UsbDevice device) {
		return device.getProductId() == PL2303HXA_PRODUCT_ID;
	}

	/**
	 * Get all supported devices, that is, get all PL2303HXA devices
	 * 
	 * @param context
	 *            Context
	 * @return Device List
	 */
	public static List<UsbDevice> getAllSupportedDevices(Context context) {
		List<UsbDevice> res = new ArrayList<UsbDevice>();
		UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while (deviceIterator.hasNext()) 
		{
			UsbDevice device = deviceIterator.next();
			boolean flag = isSupportedDevice(device);
			Log.i(TAG, getDeviceString(device) + (flag ? "" : " not support!"));
			if (flag)
				res.add(device);
		}
		return res;
	}

	/**
	 * Get device description string
	 * 
	 * @param device
	 *            equipment
	 * @return Description string
	 */
	public static String getDeviceString(UsbDevice device) {
		return String.format("DID:%d VID:%04X PID:%04X", Integer.valueOf(device.getDeviceId()), Integer.valueOf(device.getVendorId()), Integer.valueOf(device.getProductId()));
	}

	/**
	 * Get a USB device
	 * 
	 * @return USB equipment
	 */
	public UsbDevice getUsbDevice() {
		return usbDevice;
	}

	/**
	 * Create a PL2303HXA communication instance
	 * 
	 * @param context
	 *            Context
	 * @param device
	 *            USB device
	 */
	public PL2303Driver(Context context, UsbDevice device) {
		this.context = context;
		this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
		if (!isSupportedDevice(device)) {
			android.util.Log.e(TAG, getDeviceString(device) + "may is not a supported device");
		}
		this.usbDevice = device;
	}

	/**
	 Get device ID
	 *
	 * @return device ID
	 */
	public int getDeviceID() {
		return this.usbDevice.getDeviceId();
	}

	/**
	 * Converted to a string description
	 *
	 * @return description string
	 */
	public String toString() {
		return String.format("DeviceID:%d VendorID:%04X ProductID:%04X R:%d T:%d", Integer.valueOf(usbDevice.getDeviceId()), Integer.valueOf(usbDevice.getVendorId()), Integer.valueOf(usbDevice.getProductId()), receivecount, sendcount);
	}

	/**
	 * Get device name
	 *
	 * @return device noun
	 */
	public String getName() {
		return String.format("PL2303_%d", usbDevice.getDeviceId());
	}

	/**
	 * Check permissions, if you don’t have permission, ask for authorization
	 *
	 * @return true has permission, false has no permission, and start requesting authorization
	 */
	public boolean checkPermission() {
		return checkPermission(true);
	}

	/**
	 * Check permissions
	 *
	 * @param autoRequest
	 * Whether to automatically request permission when there is no permission,
	 * true automatically requests, false does not automatically request
	 * @return true has permission, false has no permission, and start requesting authorization
	 */
	public boolean checkPermission(boolean autoRequest) {
		if (!usbManager.hasPermission(usbDevice)) {
			if (autoRequest) {
				PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_PL2303_PERMISSION), 0);
				usbManager.requestPermission(usbDevice, mPermissionIntent);
			}
			return false;
		}
		return true;
	}
	
	public boolean getCTS()
	{
		/* return (mStatusLines & UART_CTS) == UART_CTS; */
		return (getStatus() & UART_CTS) == UART_CTS;
	}
	
	
	public boolean getDSR()
	{
		/* return (mStatusLines & UART_DSR) == UART_DSR; */
		return (getStatus() & UART_DSR) == UART_DSR;
	}

/*
	private final int getStatus()
	{
		if ((mReadStatusThread == null) && (mReadStatusException == null)) 
		{
			synchronized (mReadStatusThreadLock)
			{
				if (mReadStatusThread == null)
				{
					mReadStatusThread = new Thread(new Runnable() 
					{
						@Override
						public void run() 
						{
							try
							{
								while (!mStopReadStatusThread)
								{
									byte[] buffer = new byte[10];
									int readBytesCount = usbDeviceConnection.bulkTransfer(uectrl, buffer, 10, 500);
									if (readBytesCount > 0)
									{
										if (readBytesCount == 10)
										{
											mStatusLines = buffer[8];
										}
										else
										{
											throw new PL2303Exception(String.format("Invalid CTS / DSR / CD / RI status buffer received, expected %d bytes, but received %d", 10, readBytesCount));
										}
									}
								}
							}
							catch (PL2303Exception e)
							{
								e.printStackTrace();
							}
						}
					});
					mReadStatusThread.setDaemon(true);
					mReadStatusThread.start();
				}
			}
		}

		//throw and clear an exception which occurred in the status read thread
		if (mReadStatusException != null) 
		{
			mReadStatusException = null;
		}

		return mStatusLines;
	}
	*/

	public int getStatus()
	{
		try
		{
			byte[] buffer = new byte[10];
			int readBytesCount = usbDeviceConnection.bulkTransfer(uectrl, buffer, 10, 500);
			if (readBytesCount > 0)
			{
				if (readBytesCount == 10)
				{
					mStatusLines = buffer[8];
				}
				else
				{
					throw new PL2303Exception(String.format("Invalid CTS / DSR / CD / RI status buffer received, expected %d bytes, but received %d", 10, readBytesCount));
				}
			}
		}
		catch (PL2303Exception e)
		{
			e.printStackTrace();
		}
		return mStatusLines;
	}

	/**
	 * Get the baud rate
	 *
	 * @return baud rate
	 */
	public int getBaudRate() {
		return baudRate;
	}
	
	public int getSBits() {
		return SBits;
	}
	
	public int getParity() {
		return Parity;
	}

	public int getDBits() {
		return DBits;
	}

	/**
	 * Set the baud rate, if the serial port is already open, the serial port will be reset
	 *
	 * @param baudRate
	 * New baud rate
	 * @throws PL2303Exception
	 * Error in setting the baud rate
	 */
	public void setBaudRate(int baudRate) throws PL2303Exception {
		this.baudRate = baudRate;
		if (this.isOpened()) {
			this.reset();
		}
	}
	
	public void setSBits(int SBits) throws PL2303Exception {
		this.SBits = SBits;
		if (this.isOpened()) {
			this.reset();
		}
	}
	
	public void setParity(int Parity) throws PL2303Exception {
		this.Parity = Parity;
		if (this.isOpened()) {
			this.reset();
		}
	}

	public void setDBits(int DBits) throws PL2303Exception {
		this.DBits = DBits;
		if (this.isOpened()) {
			this.reset();
		}
	}

	/**
	 * Open the PL2303HXA device
	 *
	 * @throws PL2303Exception
	 * Open failure exception, if the setting parameters are not authorized or not supported
	 */
	public void open() throws PL2303Exception 
	{
		usbDeviceConnection = usbManager.openDevice(usbDevice);
		if (usbDeviceConnection != null) {
			Log.i(TAG, "openDevice()=>ok!");
			Log.i(TAG, "getInterfaceCount()=>" + usbDevice.getInterfaceCount());

			usbInterface = usbDevice.getInterface(0);
			
			for (int i = 0; i < usbInterface.getEndpointCount(); ++i) 
			{
				UsbEndpoint ue = usbInterface.getEndpoint(i);
				if (ue.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ue.getDirection() == UsbConstants.USB_DIR_IN) 
				{
					uein = ue;
				} 
				else if (ue.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ue.getDirection() == UsbConstants.USB_DIR_OUT) 
				{
					ueout = ue;
				}
				else if (ue.getType() == UsbConstants.USB_ENDPOINT_XFER_INT && ue.getDirection() == UsbConstants.USB_DIR_IN) 
				{
					uectrl = ue;
				}
			}
			if (uein != null && ueout != null && uectrl !=null)
			{
				Log.i(TAG, "get Endpoint ok!");
				usbDeviceConnection.claimInterface(usbInterface, true);
				byte[] buffer = new byte[1];
				controlTransfer(192, 1, 33924, 0, buffer, 1, transferTimeOut);
				controlTransfer(64, 1, 1028, 0, null, 0, transferTimeOut);
				controlTransfer(192, 1, 33924, 0, buffer, 1, transferTimeOut);
				controlTransfer(192, 1, 33667, 0, buffer, 1, transferTimeOut);
				controlTransfer(192, 1, 33924, 0, buffer, 1, transferTimeOut);
				controlTransfer(64, 1, 1028, 1, null, 0, transferTimeOut);
				controlTransfer(192, 1, 33924, 0, buffer, 1, transferTimeOut);
				controlTransfer(192, 1, 33667, 0, buffer, 1, transferTimeOut);
				controlTransfer(64, 1, 0, 1, null, 0, transferTimeOut);
				controlTransfer(64, 1, 1, 0, null, 0, transferTimeOut);
				controlTransfer(64, 1, 2, 68, null, 0, transferTimeOut);
				reset();
				mStopReadStatusThread = false;
				getStatus();
				opened = true;
			}
		} 
		else 
		{
			Log.e(TAG, "openDevice()=>fail!");
			throw new PL2303Exception("usbManager.openDevice failed!");
		}
	}

	/**
	 * Reset the serial port
	 *
	 * @throws PL2303Exception
	 * Unsupported parameter exception
	 */
	public void reset() throws PL2303Exception 
	{
		byte[] mPortSetting = new byte[7];
		controlTransfer(161, 33, 0, 0, mPortSetting, 7, transferTimeOut);
		mPortSetting[0] = (byte) (baudRate & 0xff);
		mPortSetting[1] = (byte) (baudRate >> 8 & 0xff);
		mPortSetting[2] = (byte) (baudRate >> 16 & 0xff);
		mPortSetting[3] = (byte) (baudRate >> 24 & 0xff);
		mPortSetting[4] = (byte) (SBits); // 0=1stop, 2=2stop bits
		mPortSetting[5] = (byte) (Parity); // 0=none,1=odd,2=even
		mPortSetting[6] = (byte) (DBits); // data bits (5,6,7,8)
		controlTransfer(33, 32, 0, 0, mPortSetting, 7, transferTimeOut);
		controlTransfer(161, 33, 0, 0, mPortSetting, 7, transferTimeOut);
		// break control disable
		controlTransfer(BREAK_REQUEST_TYPE, BREAK_REQUEST, BREAK_OFF, 0, null, 0, TimeOut);
	}


	private int controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) throws PL2303Exception 
	{
		int res = this.usbDeviceConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);
		if (res < 0) 
		{
			String err = String.format("controlTransfer fail when: %d %d %d %d buffer %d %d", requestType, request, value, index, length, timeout);
			Log.e(TAG, err);
			throw new PL2303Exception(err);
		}
		return res;
	}

	/**
	 * Is the device turned on
	 *
	 * @return true is turned on, false is not turned on
	 */
	public boolean isOpened() {
		return opened;
	}

	/**
	 * Send a byte to the serial port
	 *
	 * @param data
	 * Data to be sent
	 * @return the number of bytes sent
	 */
	public int write(byte data) 
	{
		send_buf[0] = data;
		int ret = usbDeviceConnection.bulkTransfer(ueout, send_buf, 1, writeTimeOut);
		++sendcount;
		return ret;
	}

	/**
	 * Send a string of data
	 *
	 * @param datas
	 * Data array to be sent
	 * @return the number of bytes sent
	 */
	public int write(byte[] datas) 
	{
		int ret = usbDeviceConnection.bulkTransfer(ueout, datas, datas.length, writeTimeOut);
		sendcount += ret;
		return ret;
	}

	private int readix = 0;
	private int readlen = 0;
	private boolean readSuccess = false;

	/**
	 * To read a byte, after calling this function, you need to call isReadSuccess()
	 * to determine whether the returned data is the real read data
	 *
	 * @return the data being read
	 */
	public byte read() 
	{
		byte ret = 0;
		if (readix >= readlen) 
		{
			readlen = usbDeviceConnection.bulkTransfer(uein, recv_buf, SECVBUF_LEN, readTimeOut);
			readix = 0;
		}
		if (readix < readlen) 
		{
			ret = recv_buf[readix];
			readSuccess = true;
			++receivecount;
			++readix;
		} 
		else 
		{
			readSuccess = false;
		}
		return ret;
	}

	/**
	 * Determine whether the previous read operation was successful
	 *
	 * @return
	 */
	public boolean isReadSuccess() {
		return readSuccess;
	}

	/**
	 * Close the serial port
	 */
	public void close() 
	{
		if (this.opened) 
		{
			if (usbDeviceConnection.releaseInterface(usbInterface))
				Log.i(TAG, "releaseInterface()=>ok!");
		}
		this.opened = false;
		mStopReadStatusThread = true;
	}

	/**
	 * Clear the read buffer
	 */
	public void cleanRead() 
	{
		while ((readlen = usbDeviceConnection.bulkTransfer(uein, recv_buf, recv_buf.length, readTimeOut)) > 0) 
		{

		}
		readlen = 0;
		readix = 0;
	}

	public int getReadTimeOut() {
		return readTimeOut;
	}

	public void setReadTimeOut(int readTimeOut) {
		this.readTimeOut = readTimeOut;
	}

	public int getWriteTimeOut() {
		return writeTimeOut;
	}

	public void setWriteTimeOut(int writeTimeOut) {
		this.writeTimeOut = writeTimeOut;
	}
	
	/**
	 * Switch RTS on or off
	 * 
	 * @param state
	 */
	public void setRTS(boolean state) throws PL2303Exception 
	{
		if ((state) && !((mControlLines & CONTROL_RTS) == CONTROL_RTS))
			mControlLines = mControlLines + CONTROL_RTS;
		if (!(state) && ((mControlLines & CONTROL_RTS) == CONTROL_RTS))
			mControlLines = mControlLines - CONTROL_RTS;

		int ret = controlTransfer(SET_CONTROL_REQUEST_TYPE, SET_CONTROL_REQUEST, mControlLines, 0, null, 0, TimeOut);
		if (ret < 0)
			throw new PL2303Exception("Failed to set RTS to " + state);
		else
			Log.i(TAG, "RTS set to " + state);
	}
	
	
	/**
	 * Switch DTR on or off
	 * 
	 * @param state
	 */
	public void setDTR(boolean state) throws PL2303Exception 
	{
		if ((state) && !((mControlLines & CONTROL_DTR) == CONTROL_DTR))
			mControlLines = mControlLines + CONTROL_DTR;
		if (!(state) && ((mControlLines & CONTROL_DTR) == CONTROL_DTR))
			mControlLines = mControlLines - CONTROL_DTR;

		int ret = controlTransfer(SET_CONTROL_REQUEST_TYPE, SET_CONTROL_REQUEST, mControlLines, 0, null, 0, TimeOut);
		if (ret < 0)
			throw new PL2303Exception("Failed to set DTR to " + state);
		else
			Log.i(TAG, "RTS set to " + state);
	}
	
	
}
