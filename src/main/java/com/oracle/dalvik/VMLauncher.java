package com.oracle.dalvik;

public final class VMLauncher {
	private VMLauncher() {
	}
	public static native int launchJVM(String[] args, boolean usePojavSigHandler);

	static {
		System.loadLibrary("jrelauncher");
	}
}
