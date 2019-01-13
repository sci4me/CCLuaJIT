package com.sci.cclj;

public final class LuaError extends RuntimeException {
	public LuaError(final String message) {
		super(message);
	}
}