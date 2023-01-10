package com.shablovskiy91.android.usb;

import android.content.Context;

/**
 * Authorization Information Hold <br/>
 * 
 * @author trb
 * @date 2014-1-22
 */
public class USBPermissionHolder {
	public String action;
	public Context context;
	public USBPermission.PermissionCallback callback;

	public USBPermissionHolder(String action, Context context, USBPermission.PermissionCallback callback) {
		this.action = action;
		this.context = context;
		this.callback = callback;
	}
}
