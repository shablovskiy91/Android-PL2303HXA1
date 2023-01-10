package com.shablovskiy91.android.usb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * USB Tools <br/>
 * This class is used for USB operations
 * 
 * @author trb
 * @date 2014-1-22
 */
public class USBUtil {

	private static Context context;
	private static UsbManager usbManager;
	private static String TAG = "USBUtil";

	private static void updateStaticVar(Context context) {
		if (USBUtil.context != context) {
			Log.i(TAG, "updateAllStatic");
			USBUtil.context = context;
			USBUtil.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		}
	}

	public static UsbManager getUsbManager(Context context) {
		updateStaticVar(context);
		return usbManager;
	}

	/**
	 * Get all USB devices with specified vendor ID and product ID
	 * 
	 * @param context
	 *            the context
	 * @param vendorId
	 *            Manufacturer ID, no judgment when -1
	 * @param productId
	 *            Product ID, no judgment when -1
	 * @return Compliant Device List
	 */
	public static List<UsbDevice> getUsbDevices(Context context, int vendorId, int productId) {
		updateStaticVar(context);

		HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = devices.values().iterator();
		List<UsbDevice> retDevices = new ArrayList<UsbDevice>();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();
			if ((vendorId == -1 || device.getVendorId() == vendorId) && (productId == -1 || device.getProductId() == productId)) {
				retDevices.add(device);
			}
		}

		return retDevices;
	}

	/**
	 * Get the USB device with the specified vendor ID and product ID
	 * 
	 * @param context
	 *            the context
	 * @param vendorId
	 *            Manufacturer ID, no judgment when -1
	 * @param productId
	 *            Product ID, no judgment when -1
	 * @return
	 */
	public static UsbDevice getUsbDevice(Context context, int vendorId, int productId) {
		List<UsbDevice> devices = getUsbDevices(context, vendorId, productId);
		return devices.size() > 0 ? devices.get(0) : null;
	}
}
