package com.shablovskiy91.android.usb;

import java.util.HashMap;
import java.util.Map;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

/**
 * USB authorization related <br/>
 * 
 * @author trb
 * @date 2014-1-22
 */
public class USBPermission {
	public interface PermissionCallback {
		public boolean callback(boolean granted);
	}

	static Map<String, USBPermissionHolder> perHolders = new HashMap<String, USBPermissionHolder>();

	public static boolean hasPermission(Context context, UsbDevice usbDevice) {
		return USBUtil.getUsbManager(context).hasPermission(usbDevice);
	}

	/**
	 * Request the user to obtain the operation permission of a USB device
	 * 
	 * @param context
	 *            the context
	 * @param usbDevice
	 *            USB device that needs permission
	 * @param callback
	 *            Callback, which will be called when permission is obtained
	 * @return Returns true if there is permission before the request, and returns false if there is no permission
	 */
	public static boolean requestPermission(Context context, UsbDevice usbDevice, PermissionCallback callback) {
		return requestPermission(context, usbDevice, "action" + System.currentTimeMillis(), callback);
	}

	/**
	 * Request the user to obtain the operation permission of a USB device
	 *
	 * @param context
	 *            the context
	 * @param usbDevice
	 *            USB device that needs permission
	 * @param callback
	 *            Callback, which will be called when permission is obtained
	 * @return Returns true if there is permission before the request, and returns false if there is no permission
	 */
	public static boolean requestPermission(Context context, UsbDevice usbDevice, String action, PermissionCallback callback) {
		if (USBPermission.hasPermission(context, usbDevice)) {
			if (callback != null) {
				callback.callback(true);
			}
			return true;
		} else {
			PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(action), 0);
			IntentFilter filter = new IntentFilter(action);
			filter.addAction(action);
			context.registerReceiver(permissionReceiver, filter);
			USBPermissionHolder holder = new USBPermissionHolder(action, context, callback);
			perHolders.put(action, holder);
			USBUtil.getUsbManager(context).requestPermission(usbDevice, mPermissionIntent);
			return false;
		}
	}

	// broadcast receiver
	private final static BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (perHolders.containsKey(action)) {
				USBPermissionHolder holder = perHolders.get(action);
				if (holder.callback != null)
					holder.callback.callback(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
				perHolders.remove(action);
				holder.context.unregisterReceiver(permissionReceiver);
			}
		}
	};
}
