package com.shablovskiy91.android.usb.pl2303hxa;

import java.util.List;

/**
 * PL2303 The selector information is kept. Since the complete dialog class is not used,
 * it is necessary to use this class instance to report and save the relevant operations in the selection process
 *
 * @author trb
 * @date 2013-11-18
 */
public class PL2303SelectorHolder {
	public List<PL2303Driver> drivers = null;
	public int curDriverIndex = -1;

	/**
	 * Get the currently selected driver
	 * @return currently selected driver
	 */
	public PL2303Driver getCurDriver() {
		return curDriverIndex < 0 ? null : drivers.get(curDriverIndex);
	}
}
