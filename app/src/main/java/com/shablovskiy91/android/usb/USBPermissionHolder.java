package com.shablovskiy91.android.usb;

import android.content.Context;

/**
 * 授权信息保持 <br/>
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
