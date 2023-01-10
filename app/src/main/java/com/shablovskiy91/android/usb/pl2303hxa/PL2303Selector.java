package com.shablovskiy91.android.usb.pl2303hxa;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.usb.UsbDevice;
import android.util.Log;

/**
 * PL2303 selection is related, the selection operation when there are multiple PL2303 devices in this type of package
 * 
 * @author trb
 * @date 2013-11-18
 * 
 */
public class PL2303Selector {
	private static final String TAG = "PL2303Selector";
	public static int DEFAULT_BAUDRATE = 2400;

	public interface Callback {
		public boolean whenPL2303Selected(PL2303Driver driver);
	}

	/**
	 * Select the PL2303 device, prompt information when there is no device,
	 * automatically select when there is only one device and have it,
	 * and pop up a selection dialog box when there are multiple devices or unauthorized
	 * 
	 * @param context
	 *            the context
	 * @param seldevid
	 *            The device ID selected by default, -1 is not the default
	 * @param autosel
	 *            Whether there is only one and is automatically selected when authorized
	 * @param callback
	 *            select callback
	 */
	public static void selectDevice(final Context context, int seldevid, boolean autosel, final Callback callback) {
		Dialog dlg = createSelectDialog(context, seldevid, autosel, callback);
		if (dlg != null) {
			dlg.show();
		}
	}

	/**
	 * Create a dialog box for selecting PL2303 devices, prompt information when there is no device,
	 * automatically select when there is only one device and have it,
	 * and pop up a selection dialog box when there are multiple devices or unauthorized
	 *
	 * @param context
	 *            the context
	 * @param seldevid
	 *            The device ID selected by default, -1 is not the default
	 * @param autosel
	 *            Whether there is only one and is automatically selected when authorized
	 * @param callback
	 *            select callback
	 */
	public static Dialog createSelectDialog(final Context context, int seldevid, boolean autosel, final Callback callback) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		final Dialog dlg;
		builder.setTitle("Select PL2303 device");
		List<UsbDevice> devs = PL2303Driver.getAllSupportedDevices(context);
		if (devs.size() > 0) {
			// With PL2303 equipment
			final PL2303SelectorHolder holder = new PL2303SelectorHolder();
			
			holder.drivers = new ArrayList<PL2303Driver>();
			String[] sitems = new String[devs.size()];
			int selix = -1;
			for (int i = 0; i < devs.size(); ++i) {
				PL2303Driver dr = new PL2303Driver(context, devs.get(i));
				if (dr.getDeviceID() == seldevid) {
					selix = i;
				}
				try {
					dr.setBaudRate(DEFAULT_BAUDRATE);
				} catch (PL2303Exception e) {
					e.printStackTrace();
				}
				String name = dr.getName();
				sitems[i] = name;
				holder.drivers.add(dr);
			}

			if (autosel && holder.drivers.size() == 1 && holder.drivers.get(0).checkPermission(false)) {
				// Do not show dialog when there is only one device
				if (callback != null) {
					callback.whenPL2303Selected(holder.drivers.get(0));
				}
				return null;
			}

			builder.setSingleChoiceItems(sitems, selix, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (holder.curDriverIndex != which || holder.curDriverIndex < 0 || holder.drivers.get(holder.curDriverIndex).isOpened() == false) {
						holder.curDriverIndex = which;
						Log.i(TAG, "clk: " + which);
					}
				}
			});
			builder.setPositiveButton("Sure", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (callback != null) {
						if (holder.getCurDriver() != null && holder.getCurDriver().isOpened())
							holder.getCurDriver().close();
						if (holder.getCurDriver() == null)
							return;
						callback.whenPL2303Selected(new PL2303Driver(context, holder.getCurDriver().getUsbDevice()));
					}
				}
			});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			dlg = builder.create();
		} else {
			// 没有设备
			builder.setMessage("Did not find any available PL2303 devices");
			builder.setPositiveButton("Sure", null);
			dlg = builder.create();
		}
		return dlg;
	}
}
