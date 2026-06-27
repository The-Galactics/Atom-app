package com.atom.infrastructure.adapter.accessibility.capture;

/** Outcome of a capture attempt; distinguishes a real empty screen from failure. */
public enum CaptureStatus { READY, TIMEOUT, UNAVAILABLE }
